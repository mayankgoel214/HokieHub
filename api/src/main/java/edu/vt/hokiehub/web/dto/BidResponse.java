package edu.vt.hokiehub.web.dto;

import edu.vt.hokiehub.domain.Bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A bid as the seller — or the bidder themselves — sees it, with who made it.
 * A passer-by never gets this shape; they get {@link BidSummaryResponse}.
 */
public record BidResponse(
        UUID id,
        ListingResponse.SellerSummary bidder,
        BigDecimal amount,
        String message,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static BidResponse from(Bid bid) {
        return new BidResponse(
                bid.getId(),
                new ListingResponse.SellerSummary(bid.getBidder().getId(), bid.getBidder().getFullName(),
                        bid.getBidder().getEmail()),
                bid.getAmount(),
                bid.getMessage(),
                bid.getStatus().value(),
                bid.getCreatedAt(),
                bid.getUpdatedAt());
    }
}
