package edu.vt.hokiehub.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "listing_images")
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "is_primary")
    private Boolean primary = false;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    protected ListingImage() {}

    public ListingImage(String imageUrl, boolean primary, int displayOrder) {
        this.imageUrl = imageUrl;
        this.primary = primary;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }

    public Integer getId() { return id; }
    public Listing getListing() { return listing; }
    void setListing(Listing listing) { this.listing = listing; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getPrimary() { return primary; }
    public Integer getDisplayOrder() { return displayOrder; }
}
