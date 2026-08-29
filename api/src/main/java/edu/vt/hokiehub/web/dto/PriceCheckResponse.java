package edu.vt.hokiehub.web.dto;

import edu.vt.hokiehub.domain.PriceCheck;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The price check as a buyer sees it.
 *
 * The sources are part of the contract, not an extra: an estimate is only ever
 * returned alongside what it was drawn from.
 */
public record PriceCheckResponse(
        String status,
        String identifiedItem,
        BigDecimal estimatedLow,
        BigDecimal estimatedTypical,
        BigDecimal estimatedHigh,
        String verdict,
        String summary,
        String failureReason,
        boolean grounded,
        String model,
        List<Source> sources,
        Instant createdAt
) {
    public record Source(String title, String url, BigDecimal price, String note) {}

    /** What a page needs before deciding whether to offer the purchase. */
    public record Status(boolean available, int priceCents, boolean unlocked) {}

    public static PriceCheckResponse from(PriceCheck c) {
        return new PriceCheckResponse(
                c.getStatus().value(),
                c.getIdentifiedItem(),
                c.getEstimatedLow(),
                c.getEstimatedTypical(),
                c.getEstimatedHigh(),
                c.getVerdict() == null ? null : c.getVerdict().value(),
                c.getSummary(),
                c.getFailureReason(),
                Boolean.TRUE.equals(c.getGrounded()),
                c.getModel(),
                c.getSources().stream()
                        .map(s -> new Source(s.getTitle(), s.getUrl(), s.getPrice(), s.getNote()))
                        .toList(),
                c.getCreatedAt());
    }
}
