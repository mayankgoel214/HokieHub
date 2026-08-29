package edu.vt.hokiehub.service;

import edu.vt.hokiehub.domain.*;
import edu.vt.hokiehub.exception.ForbiddenException;
import edu.vt.hokiehub.exception.NotFoundException;
import edu.vt.hokiehub.repository.BidRepository;
import edu.vt.hokiehub.repository.ListingRepository;
import edu.vt.hokiehub.repository.UserRepository;
import edu.vt.hokiehub.web.dto.BidSummaryResponse;
import edu.vt.hokiehub.web.dto.PlaceBidRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Offers on listings.
 *
 * Every rule that decides who may do what lives here rather than in the
 * controller, for the same reason listing ownership does: there is one place to
 * read, and it can be tested without HTTP.
 */
@Service
public class BidService {

    private final BidRepository bids;
    private final ListingRepository listings;
    private final UserRepository users;

    public BidService(BidRepository bids, ListingRepository listings, UserRepository users) {
        this.bids = bids;
        this.listings = listings;
        this.users = users;
    }

    /**
     * Places an offer, or raises the caller's existing one.
     *
     * A second bid from the same person updates the first rather than adding to
     * it. Otherwise "withdraw my bid" would be ambiguous, and anyone could pad a
     * seller's list to manufacture the look of interest.
     */
    @Transactional
    public Bid place(UUID listingId, Caller caller, PlaceBidRequest request) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));

        if (listing.isOwnedBy(caller.id())) {
            throw new ForbiddenException("You cannot bid on your own listing");
        }
        if (listing.getStatus() != ListingStatus.AVAILABLE) {
            throw new ForbiddenException(
                    "This listing is " + listing.getStatus().value() + " and is not taking offers");
        }

        User bidder = users.findById(caller.id()).orElseGet(() -> provision(caller));

        return withBidderLoaded(bids.findByListingIdAndBidderId(listingId, caller.id())
                .map(existing -> {
                    existing.setAmount(request.amount());
                    existing.setMessage(request.message());
                    // Coming back after withdrawing, or after the seller declined, is
                    // a new offer and should be live again.
                    existing.setStatus(BidStatus.ACTIVE);
                    return existing;
                })
                .orElseGet(() -> bids.save(
                        new Bid(listing, bidder, request.amount(), request.message()))));
    }

    /** The bidder taking their own offer off the table. */
    @Transactional
    public void withdraw(UUID listingId, String userId) {
        Bid bid = bids.findByListingIdAndBidderId(listingId, userId)
                .orElseThrow(() -> new NotFoundException("You have no bid on this listing"));
        bid.setStatus(BidStatus.WITHDRAWN);
    }

    /** Only the seller may see who is bidding. */
    @Transactional(readOnly = true)
    public List<Bid> forSeller(UUID listingId, String userId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));
        if (!listing.isOwnedBy(userId)) {
            throw new ForbiddenException("Only the seller can see the offers on a listing");
        }
        return bids.findByListingIdAndStatusInOrderByAmountDesc(
                listingId, List.of(BidStatus.ACTIVE, BidStatus.ACCEPTED));
    }

    @Transactional(readOnly = true)
    public List<Bid> placedBy(String userId) {
        return bids.findByBidderIdAndStatusOrderByUpdatedAtDesc(userId, BidStatus.ACTIVE);
    }

    /**
     * The seller takes an offer. The listing goes to pending rather than sold —
     * the two of them still have to meet — and every other offer is declined, so
     * nobody is left believing they are still in the running.
     */
    @Transactional
    public Bid accept(UUID listingId, UUID bidId, String userId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Listing " + listingId + " not found"));
        if (!listing.isOwnedBy(userId)) {
            throw new ForbiddenException("Only the seller can accept an offer");
        }

        Bid bid = bids.findById(bidId)
                .orElseThrow(() -> new NotFoundException("Bid " + bidId + " not found"));
        if (!bid.getListing().getId().equals(listingId)) {
            throw new NotFoundException("Bid " + bidId + " is not on this listing");
        }
        if (bid.getStatus() != BidStatus.ACTIVE) {
            throw new ForbiddenException("That offer is " + bid.getStatus().value()
                    + " and can no longer be accepted");
        }

        bid.setStatus(BidStatus.ACCEPTED);
        bids.declineOthers(listingId, bidId, BidStatus.ACTIVE, BidStatus.DECLINED);
        listing.setStatus(ListingStatus.PENDING);
        return withBidderLoaded(bid);
    }

    /**
     * Bid counts for a page of listings, in one query.
     *
     * Returned as a map so a caller can decorate rows without going back to the
     * database per row — the whole point of fetching them together.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BidSummaryResponse> summarise(Collection<UUID> listingIds) {
        if (listingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BidSummaryResponse> byListing = new HashMap<>();
        for (BidRepository.BidSummary row : bids.summarise(listingIds, BidStatus.ACTIVE)) {
            byListing.put(row.getListingId(),
                    new BidSummaryResponse(row.getTotal(), row.getHighest()));
        }
        return byListing;
    }

    @Transactional(readOnly = true)
    public BidSummaryResponse summarise(UUID listingId) {
        return summarise(List.of(listingId)).getOrDefault(listingId, BidSummaryResponse.NONE);
    }

    /**
     * The response names the bidder, and `open-in-view` is disabled, so the proxy
     * has to be resolved here rather than by Jackson touching it after the
     * transaction has closed.
     */
    private static Bid withBidderLoaded(Bid bid) {
        bid.getBidder().getEmail();
        return bid;
    }

    /**
     * Same rule as posting a listing: a valid token from outside Virginia Tech is
     * still not a Virginia Tech account.
     */
    private User provision(Caller caller) {
        String email = caller.email() == null ? null : caller.email().trim().toLowerCase();
        if (email == null || !email.endsWith("@vt.edu")) {
            throw new ForbiddenException("HokieHub is limited to @vt.edu accounts");
        }
        return users.save(new User(caller.id(), email, caller.fullName()));
    }
}
