package edu.vt.hokiehub.pricing;

import edu.vt.hokiehub.domain.*;
import edu.vt.hokiehub.exception.ForbiddenException;
import edu.vt.hokiehub.exception.NotFoundException;
import edu.vt.hokiehub.repository.ListingRepository;
import edu.vt.hokiehub.repository.PriceCheckRepository;
import edu.vt.hokiehub.repository.PriceCheckUnlockRepository;
import edu.vt.hokiehub.repository.UserRepository;
import edu.vt.hokiehub.service.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The price check: what the item is worth, according to what comparable ones
 * actually sold for.
 *
 * Three rules live here rather than in the controller, because each is the kind
 * that has to hold on every path.
 *
 * The seller cannot buy it for their own listing. The point of the feature is
 * that a buyer can get a read the seller did not write; letting the seller see
 * it too would mostly let them price against the tool.
 *
 * An estimate is never published without comparables behind it. If the grounded
 * search comes back empty the answer is "we could not find comparables", which
 * is a true statement and a useful one. A number with nothing behind it would be
 * the model guessing, dressed as a market rate.
 *
 * The analysis is computed once per listing and shared. It costs a paid model
 * call and the answer does not depend on who asked.
 */
@Service
public class PriceCheckService {

    private static final Logger log = LoggerFactory.getLogger(PriceCheckService.class);

    /** What it would cost, if payment were wired up. It is not; unlocking is free. */
    public static final int PRICE_CENTS = 199;

    private final ListingRepository listings;
    private final PriceCheckRepository checks;
    private final PriceCheckUnlockRepository unlocks;
    private final UserRepository users;
    private final GeminiPriceEstimator estimator;

    public PriceCheckService(ListingRepository listings,
                             PriceCheckRepository checks,
                             PriceCheckUnlockRepository unlocks,
                             UserRepository users,
                             GeminiPriceEstimator estimator) {
        this.listings = listings;
        this.checks = checks;
        this.unlocks = unlocks;
        this.users = users;
        this.estimator = estimator;
    }

    public boolean isConfigured() {
        return estimator.isConfigured();
    }

    @Transactional(readOnly = true)
    public boolean hasUnlocked(UUID listingId, String userId) {
        return userId != null && unlocks.existsByListingIdAndUserId(listingId, userId);
    }

    /**
     * Records the purchase. No money moves — there is no payment processor here —
     * so this is the honest stub: the row is what the gate reads, and the amount
     * it would have cost is stored with it.
     */
    @Transactional
    public void unlock(UUID listingId, Caller caller) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));

        if (listing.isOwnedBy(caller.id())) {
            throw new ForbiddenException(
                    "The price check is for buyers. You set the price on this listing.");
        }
        if (!estimator.isConfigured()) {
            throw new GeminiPriceEstimator.PriceCheckUnavailableException(
                    "The price check is not configured on this deployment.");
        }

        provisionIfNeeded(caller);

        if (!unlocks.existsByListingIdAndUserId(listingId, caller.id())) {
            unlocks.save(new PriceCheckUnlock(listingId, caller.id(), PRICE_CENTS));
        }
    }

    /**
     * The analysis, computed on first request and reused after.
     *
     * Not re-run for a listing that already has one: it is the same item and the
     * same photographs, and every run costs a model call.
     */
    @Transactional
    public PriceCheck analyse(UUID listingId, String userId) {
        Listing listing = listings.findWithDetailsById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));

        if (listing.isOwnedBy(userId)) {
            throw new ForbiddenException(
                    "The price check is for buyers. You set the price on this listing.");
        }
        if (!unlocks.existsByListingIdAndUserId(listingId, userId)) {
            throw new ForbiddenException("Unlock the price check to see it.");
        }

        var existing = checks.findByListingId(listingId);
        if (existing.isPresent() && existing.get().getStatus() != PriceCheck.Status.FAILED) {
            return existing.get();
        }

        List<ListingImage> photos = listing.getImages().stream()
                .filter(ListingImage::isUploaded)
                .toList();

        // A previous failure is worth retrying; a previous answer is not. The
        // retry rewrites the existing row rather than replacing it — deleting and
        // inserting inside one flush let Hibernate order the insert first, which
        // collided with the unique constraint on listing_id and answered 500.
        PriceCheck check = existing.orElseGet(
                () -> new PriceCheck(listing, PriceCheck.Status.FAILED, GeminiPriceEstimator.MODEL));
        check.reset(GeminiPriceEstimator.MODEL);

        try {
            var estimate = estimator.estimate(listing, photos);

            if (estimate.comparables().isEmpty()) {
                // The honest outcome. No estimate is recorded at all, so there is
                // nothing for a later change to accidentally start displaying.
                check.setStatus(PriceCheck.Status.NO_COMPARABLES);
                check.setIdentifiedItem(estimate.identifiedItem());
                check.setSummary(estimate.summary());
                check.setGrounded(false);
                return checks.save(check);
            }

            if (estimate.typical() == null) {
                check.setStatus(PriceCheck.Status.NO_COMPARABLES);
                check.setIdentifiedItem(estimate.identifiedItem());
                check.setSummary(estimate.summary());
                check.setGrounded(false);
                return checks.save(check);
            }

            check.setStatus(PriceCheck.Status.READY);
            check.setIdentifiedItem(estimate.identifiedItem());
            check.setEstimatedLow(estimate.low() != null ? estimate.low() : estimate.typical());
            check.setEstimatedTypical(estimate.typical());
            check.setEstimatedHigh(estimate.high() != null ? estimate.high() : estimate.typical());
            check.setSummary(estimate.summary());
            check.setGrounded(true);
            // Decided here from the numbers, not asked of the model: a verdict is
            // arithmetic, and arithmetic should not be delegated to something that
            // is only usually right.
            check.decideVerdict(listing.getPrice());

            int order = 0;
            for (var c : estimate.comparables()) {
                check.addSource(new PriceCheckSource(c.title(), c.url(), c.price(), c.note(), order++));
            }
            return checks.save(check);

        } catch (GeminiPriceEstimator.PriceCheckUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Price check failed for listing {}: {}", listingId, e.toString());
            check.setStatus(PriceCheck.Status.FAILED);
            check.setFailureReason(e.getMessage());
            return checks.save(check);
        }
    }

    /** Same rule as everywhere else: a valid token is not a Virginia Tech account. */
    private void provisionIfNeeded(Caller caller) {
        if (users.existsById(caller.id())) return;
        String email = caller.email() == null ? null : caller.email().trim().toLowerCase();
        if (email == null || !email.endsWith("@vt.edu")) {
            throw new ForbiddenException("HokieHub is limited to @vt.edu accounts");
        }
        users.save(new User(caller.id(), email, caller.fullName()));
    }
}
