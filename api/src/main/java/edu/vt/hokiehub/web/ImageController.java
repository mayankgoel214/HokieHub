package edu.vt.hokiehub.web;

import edu.vt.hokiehub.domain.Listing;
import edu.vt.hokiehub.domain.ListingImage;
import edu.vt.hokiehub.exception.ForbiddenException;
import edu.vt.hokiehub.exception.NotFoundException;
import edu.vt.hokiehub.repository.ListingImageRepository;
import edu.vt.hokiehub.repository.ListingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Photographs of the actual item.
 *
 * A listing without a real picture falls back to a generated cover, which is
 * honest but is not the thing being sold. It also makes the price check
 * impossible: there is nothing to look at.
 */
@RestController
@Tag(name = "Images", description = "Photographs attached to a listing")
public class ImageController {

    /** Enough for a legible photograph, small enough that the table stays sane. */
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int MAX_PER_LISTING = 5;
    private static final List<String> ALLOWED =
            List.of("image/jpeg", "image/png", "image/webp");

    private final ListingRepository listings;
    private final ListingImageRepository images;

    public ImageController(ListingRepository listings, ListingImageRepository images) {
        this.listings = listings;
        this.images = images;
    }

    @GetMapping("/api/images/{id}")
    @Operation(summary = "Fetch an uploaded photograph", description = "Public.")
    @Transactional
    public ResponseEntity<byte[]> get(@PathVariable Integer id) {
        ListingImage image = images.findById(id)
                .orElseThrow(() -> new NotFoundException("Image " + id + " not found"));

        byte[] bytes = image.getData();
        if (bytes == null) {
            throw new NotFoundException("Image " + id + " has no stored content");
        }

        return ResponseEntity.ok()
                // The bytes for an id never change — a replacement is a new row — so
                // this can be cached hard and immutably.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(
                        image.getContentType() == null ? "image/jpeg" : image.getContentType()))
                .body(bytes);
    }

    @PostMapping(path = "/api/listings/{listingId}/images",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a photograph to a listing you own")
    @Transactional
    public ResponseEntity<ImageResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID listingId,
                                                @RequestParam("file") MultipartFile file) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));
        if (!listing.isOwnedBy(jwt.getSubject())) {
            throw new ForbiddenException("You can only add photographs to your own listing");
        }

        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("A photograph must be 2 MB or smaller");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.contains(type)) {
            throw new IllegalArgumentException("Photographs must be JPEG, PNG or WebP");
        }

        long existing = images.countByListingId(listingId);
        if (existing >= MAX_PER_LISTING) {
            throw new IllegalArgumentException(
                    "A listing can have at most " + MAX_PER_LISTING + " photographs");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("That file could not be read");
        }

        // The first photograph on a listing becomes the card image.
        ListingImage image = new ListingImage(bytes, type, existing == 0, (int) existing);
        listing.addImage(image);

        // Saved directly rather than by cascading from the listing. The listing
        // already has an id, so saving it goes through merge(), which returns a
        // fresh managed copy and assigns the generated id to that — leaving the
        // reference held here with a null id, and the caller with a URL of
        // /api/images/null. Persisting the child returns the instance that was
        // actually written.
        ListingImage saved = images.saveAndFlush(image);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ImageResponse(saved.getId(), saved.publicUrl(),
                        saved.getPrimary(), saved.getDisplayOrder()));
    }

    @DeleteMapping("/api/listings/{listingId}/images/{imageId}")
    @Operation(summary = "Remove a photograph from a listing you own")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable UUID listingId,
                       @PathVariable Integer imageId) {
        Listing listing = listings.findWithDetailsById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));
        if (!listing.isOwnedBy(jwt.getSubject())) {
            throw new ForbiddenException("You can only remove photographs from your own listing");
        }

        ListingImage target = listing.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Image " + imageId + " is not on this listing"));

        boolean wasPrimary = Boolean.TRUE.equals(target.getPrimary());
        listing.getImages().remove(target);

        // Removing the card image must promote another, or the listing silently
        // loses its picture and falls back to a generated cover.
        if (wasPrimary && !listing.getImages().isEmpty()) {
            listing.getImages().get(0).setPrimary(true);
        }
        listings.save(listing);
    }

    public record ImageResponse(Integer id, String url, Boolean isPrimary, Integer displayOrder) {}
}
