package edu.vt.hokiehub.web;

import edu.vt.hokiehub.repository.ListingImageRepository;
import edu.vt.hokiehub.service.BidService;
import edu.vt.hokiehub.service.Caller;
import edu.vt.hokiehub.service.ListingService;
import edu.vt.hokiehub.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
@Tag(name = "Listings", description = "Marketplace listings for items and services")
public class ListingController {

    /** Bounded so a client cannot ask for the entire table in one request. */
    private static final int MAX_PAGE_SIZE = 100;

    private final ListingService service;
    private final BidService bids;
    private final ListingImageRepository images;

    public ListingController(ListingService service, BidService bids, ListingImageRepository images) {
        this.service = service;
        this.bids = bids;
        this.images = images;
    }

    /**
     * Decorates a page with its cover images and offer counts.
     *
     * Two queries for the whole page, not two per row. Fetching these inside the
     * mapper would have reintroduced exactly the N+1 the entity graphs exist to
     * prevent, and it would not have looked like a mistake.
     */
    private PageResponse<ListingResponse> decorate(Page<edu.vt.hokiehub.domain.Listing> page) {
        var ids = page.getContent().stream().map(edu.vt.hokiehub.domain.Listing::getId).toList();
        var covers = ids.isEmpty() ? java.util.Map.<java.util.UUID, String>of()
                : images.findPrimaryFor(ids).stream().collect(java.util.stream.Collectors.toMap(
                        ListingImageRepository.PrimaryImage::getListingId,
                        ListingImageRepository.PrimaryImage::url,
                        (a, b) -> a));
        var offers = bids.summarise(ids);

        return PageResponse.from(page, l -> ListingResponse.summary(
                l, covers.get(l.getId()), offers.get(l.getId())));
    }

    @GetMapping
    @Operation(summary = "Browse listings",
               description = "Paged and filterable. `q` matches the title or description, "
                           + "case-insensitively. Filtering by a top-level category also "
                           + "returns listings filed under its subcategories.")
    public PageResponse<ListingResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String listingType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String q) {

        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return decorate(service.search(categoryId, status, listingType, minPrice, maxPrice, q, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single listing, including images and service details")
    @ApiResponse(responseCode = "404", description = "No listing with that id")
    public ListingResponse get(@PathVariable UUID id) {
        var listing = service.findById(id, true);
        return ListingResponse.detail(listing, bids.summarise(id));
    }

    @PostMapping
    @Operation(summary = "Create a listing")
    @ApiResponse(responseCode = "201", description = "Created")
    public ResponseEntity<ListingResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody CreateListingRequest request) {
        var created = service.create(Caller.from(jwt), request);
        return ResponseEntity
                .created(URI.create("/api/listings/" + created.getId()))
                .body(ListingResponse.detail(created, BidSummaryResponse.NONE));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a listing you own")
    @ApiResponse(responseCode = "403", description = "You do not own this listing")
    public ListingResponse update(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody UpdateListingRequest request) {
        return ListingResponse.detail(service.update(id, jwt.getSubject(), request),
                bids.summarise(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a listing you own")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, jwt.getSubject());
    }

    @GetMapping("/mine")
    @Operation(summary = "Listings created by the authenticated user")
    public PageResponse<ListingResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return decorate(service.findBySeller(jwt.getSubject(), pageable));
    }
}
