package edu.vt.hokiehub.repository;

import edu.vt.hokiehub.domain.Bid;
import edu.vt.hokiehub.domain.BidStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

    /** The seller's view: strongest offer first, bidder resolved in the same query. */
    @EntityGraph(attributePaths = {"bidder"})
    List<Bid> findByListingIdAndStatusOrderByAmountDesc(UUID listingId, BidStatus status);

    Optional<Bid> findByListingIdAndBidderId(UUID listingId, String bidderId);

    @EntityGraph(attributePaths = {"listing", "listing.seller", "listing.category"})
    List<Bid> findByBidderIdAndStatusOrderByUpdatedAtDesc(String bidderId, BidStatus status);

    /**
     * What a browsing visitor may know: how many offers there are and the best one,
     * never who made them.
     *
     * Batched over a whole page of listings rather than asked per row. Answering
     * this one listing at a time would put back exactly the N+1 the entity graphs
     * were added to remove — twenty-four extra round trips to decorate one page.
     */
    @Query("""
           select b.listing.id as listingId, count(b) as total, max(b.amount) as highest
           from Bid b
           where b.listing.id in :listingIds and b.status = :status
           group by b.listing.id
           """)
    List<BidSummary> summarise(@Param("listingIds") Collection<UUID> listingIds,
                               @Param("status") BidStatus status);

    /** Accepting one offer declines every other in a single statement. */
    @Modifying
    @Query("""
           update Bid b set b.status = :declined
           where b.listing.id = :listingId
             and b.id <> :acceptedId
             and b.status = :active
           """)
    void declineOthers(@Param("listingId") UUID listingId,
                       @Param("acceptedId") UUID acceptedId,
                       @Param("active") BidStatus active,
                       @Param("declined") BidStatus declined);

    /** Spring Data projection: no DTO class needed for three scalars. */
    interface BidSummary {
        UUID getListingId();
        long getTotal();
        BigDecimal getHighest();
    }
}
