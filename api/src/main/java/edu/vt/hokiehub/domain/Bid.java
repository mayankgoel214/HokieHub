package edu.vt.hokiehub.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An offer on a listing.
 *
 * There is at most one row per bidder per listing, enforced by a unique
 * constraint: raising an offer updates this row. A bidder with a stack of
 * historical bids would make "withdraw my bid" ambiguous and would let someone
 * pad a seller's list to look like interest.
 */
@Entity
@Table(name = "bids")
public class Bid {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String message;

    @Column(length = 20, nullable = false)
    private BidStatus status = BidStatus.ACTIVE;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Bid() {
    }

    public Bid(Listing listing, User bidder, BigDecimal amount, String message) {
        this.listing = listing;
        this.bidder = bidder;
        this.amount = amount;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isPlacedBy(String userId) {
        return bidder.getId().equals(userId);
    }

    public UUID getId() { return id; }
    public Listing getListing() { return listing; }
    public User getBidder() { return bidder; }
    public BigDecimal getAmount() { return amount; }
    public String getMessage() { return message; }
    public BidStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setMessage(String message) { this.message = message; }
    public void setStatus(BidStatus status) { this.status = status; }
}
