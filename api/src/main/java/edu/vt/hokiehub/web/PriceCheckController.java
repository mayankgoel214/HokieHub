package edu.vt.hokiehub.web;

import edu.vt.hokiehub.domain.PriceCheck;
import edu.vt.hokiehub.pricing.GeminiPriceEstimator;
import edu.vt.hokiehub.pricing.PriceCheckService;
import edu.vt.hokiehub.service.Caller;
import edu.vt.hokiehub.web.dto.PriceCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/listings/{listingId}/price-check")
@Tag(name = "Price check", description = "An independent read on what a listing is worth")
public class PriceCheckController {

    private final PriceCheckService prices;

    public PriceCheckController(PriceCheckService prices) {
        this.prices = prices;
    }

    @GetMapping("/status")
    @Operation(summary = "Whether the price check is available and unlocked",
               description = "Public. Says what it would cost and whether this viewer has paid.")
    public PriceCheckResponse.Status status(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID listingId) {
        String userId = jwt == null ? null : jwt.getSubject();
        return new PriceCheckResponse.Status(
                prices.isConfigured(),
                PriceCheckService.PRICE_CENTS,
                prices.hasUnlocked(listingId, userId));
    }

    @PostMapping("/unlock")
    @Operation(summary = "Buy the price check",
               description = "No payment is taken: this build records the purchase and charges nothing.")
    @ApiResponse(responseCode = "403", description = "You are the seller on this listing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        prices.unlock(listingId, Caller.from(jwt));
    }

    @GetMapping
    @Operation(summary = "The price check itself",
               description = "Requires an unlock. Never returns an estimate without the "
                           + "comparables it came from.")
    @ApiResponse(responseCode = "403", description = "Not unlocked, or you are the seller")
    public PriceCheckResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID listingId) {
        PriceCheck check = prices.analyse(listingId, jwt.getSubject());
        return PriceCheckResponse.from(check);
    }

    /**
     * A deployment without a key is a setup problem, not a client error, and it
     * should read as one rather than as a broken feature.
     */
    @ExceptionHandler(GeminiPriceEstimator.PriceCheckUnavailableException.class)
    public ProblemDetail unavailable(GeminiPriceEstimator.PriceCheckUnavailableException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        p.setTitle("Price check unavailable");
        return p;
    }
}
