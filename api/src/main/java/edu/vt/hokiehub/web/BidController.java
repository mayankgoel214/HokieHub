package edu.vt.hokiehub.web;

import edu.vt.hokiehub.service.BidService;
import edu.vt.hokiehub.service.Caller;
import edu.vt.hokiehub.web.dto.BidResponse;
import edu.vt.hokiehub.web.dto.BidSummaryResponse;
import edu.vt.hokiehub.web.dto.PlaceBidRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listings/{listingId}/bids")
@Tag(name = "Bids", description = "Offers on a listing")
public class BidController {

    private final BidService bids;

    public BidController(BidService bids) {
        this.bids = bids;
    }

    @GetMapping("/summary")
    @Operation(summary = "How many offers a listing has, and the highest",
               description = "Public. Deliberately does not say who is bidding.")
    public BidSummaryResponse summary(@PathVariable UUID listingId) {
        return bids.summarise(listingId);
    }

    @GetMapping
    @Operation(summary = "The offers on a listing, with who made them")
    @ApiResponse(responseCode = "403", description = "You are not the seller")
    public List<BidResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        return bids.forSeller(listingId, jwt.getSubject()).stream().map(BidResponse::from).toList();
    }

    @PutMapping
    @Operation(summary = "Place an offer, or raise the one you already made")
    @ApiResponse(responseCode = "403", description = "It is your own listing, or it is no longer available")
    public BidResponse place(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID listingId,
                             @Valid @RequestBody PlaceBidRequest request) {
        return BidResponse.from(bids.place(listingId, Caller.from(jwt), request));
    }

    @DeleteMapping
    @Operation(summary = "Withdraw your offer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        bids.withdraw(listingId, jwt.getSubject());
    }

    @PostMapping("/{bidId}/accept")
    @Operation(summary = "Accept an offer",
               description = "Moves the listing to pending and declines the other offers.")
    @ApiResponse(responseCode = "403", description = "You are not the seller")
    public BidResponse accept(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID listingId,
                              @PathVariable UUID bidId) {
        return BidResponse.from(bids.accept(listingId, bidId, jwt.getSubject()));
    }
}
