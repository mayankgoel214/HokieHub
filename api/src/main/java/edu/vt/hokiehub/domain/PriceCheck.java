package edu.vt.hokiehub.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An independent read on what a listing is actually worth.
 *
 * One per listing rather than one per buyer: the answer does not depend on who
 * asked, and producing it costs a paid model call.
 */
@Entity
@Table(name = "price_checks")
public class PriceCheck {

    public enum Status {
        READY("ready"),
        /** The search found nothing usable. A first-class outcome, not an error. */
        NO_COMPARABLES("no_comparables"),
        FAILED("failed");

        private final String value;
        Status(String v) { this.value = v; }
        public String value() { return value; }
        public static Status from(String v) {
            for (Status s : values()) if (s.value.equals(v)) return s;
            throw new IllegalArgumentException("Unknown price check status: " + v);
        }
    }

    public enum Verdict {
        BELOW_MARKET("below_market"), FAIR("fair"), ABOVE_MARKET("above_market");
        private final String value;
        Verdict(String v) { this.value = v; }
        public String value() { return value; }
        public static Verdict from(String v) {
            for (Verdict x : values()) if (x.value.equals(v)) return x;
            throw new IllegalArgumentException("Unknown verdict: " + v);
        }
    }

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, unique = true)
    private Listing listing;

    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "identified_item", length = 300)
    private String identifiedItem;

    @Column(name = "estimated_low", precision = 10, scale = 2)
    private BigDecimal estimatedLow;

    @Column(name = "estimated_typical", precision = 10, scale = 2)
    private BigDecimal estimatedTypical;

    @Column(name = "estimated_high", precision = 10, scale = 2)
    private BigDecimal estimatedHigh;

    @Column(length = 20)
    private Verdict verdict;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "failure_reason", length = 400)
    private String failureReason;

    @Column(length = 80)
    private String model;

    /** Whether the estimate is backed by retrieved sources rather than recall. */
    @Column(nullable = false)
    private Boolean grounded = false;

    @OneToMany(mappedBy = "priceCheck", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<PriceCheckSource> sources = new ArrayList<>();

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PriceCheck() {}

    public PriceCheck(Listing listing, Status status, String model) {
        this.listing = listing;
        this.status = status;
        this.model = model;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void addSource(PriceCheckSource source) {
        sources.add(source);
        source.setPriceCheck(this);
    }

    /** How the asking price sits against the estimate, decided here rather than by the model. */
    public void decideVerdict(BigDecimal askingPrice) {
        if (estimatedLow == null || estimatedHigh == null || askingPrice == null) {
            this.verdict = null;
            return;
        }
        if (askingPrice.compareTo(estimatedLow) < 0)       this.verdict = Verdict.BELOW_MARKET;
        else if (askingPrice.compareTo(estimatedHigh) > 0) this.verdict = Verdict.ABOVE_MARKET;
        else                                               this.verdict = Verdict.FAIR;
    }

    public UUID getId() { return id; }
    public Listing getListing() { return listing; }
    public Status getStatus() { return status; }
    public String getIdentifiedItem() { return identifiedItem; }
    public BigDecimal getEstimatedLow() { return estimatedLow; }
    public BigDecimal getEstimatedTypical() { return estimatedTypical; }
    public BigDecimal getEstimatedHigh() { return estimatedHigh; }
    public Verdict getVerdict() { return verdict; }
    public String getSummary() { return summary; }
    public String getFailureReason() { return failureReason; }
    public String getModel() { return model; }
    public Boolean getGrounded() { return grounded; }
    public List<PriceCheckSource> getSources() { return sources; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(Status s) { this.status = s; }
    public void setIdentifiedItem(String v) { this.identifiedItem = v; }
    public void setEstimatedLow(BigDecimal v) { this.estimatedLow = v; }
    public void setEstimatedTypical(BigDecimal v) { this.estimatedTypical = v; }
    public void setEstimatedHigh(BigDecimal v) { this.estimatedHigh = v; }
    public void setSummary(String v) { this.summary = v; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public void setGrounded(boolean v) { this.grounded = v; }
}
