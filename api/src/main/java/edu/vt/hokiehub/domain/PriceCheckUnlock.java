package edu.vt.hokiehub.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * A buyer who has paid to see the price check on a listing.
 *
 * No payment processor is wired up: amountCents records what it would have cost
 * and unlocking is free in this build. The row exists so the gate is enforced on
 * the server rather than by hiding a section of the page.
 */
@Entity
@Table(name = "price_check_unlocks")
public class PriceCheckUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents = 199;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    protected PriceCheckUnlock() {}

    public PriceCheckUnlock(UUID listingId, String userId, int amountCents) {
        this.listingId = listingId;
        this.userId = userId;
        this.amountCents = amountCents;
    }

    @PrePersist
    void onCreate() { if (unlockedAt == null) unlockedAt = Instant.now(); }

    public Long getId() { return id; }
    public UUID getListingId() { return listingId; }
    public String getUserId() { return userId; }
    public Integer getAmountCents() { return amountCents; }
    public Instant getUnlockedAt() { return unlockedAt; }
}
