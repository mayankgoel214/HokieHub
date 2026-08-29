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

    /**
     * A link to an image held somewhere else — the generated category covers use
     * this. Null for an uploaded photograph, which is served from this service
     * instead. The schema requires exactly one of the two.
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_primary")
    private Boolean primary = false;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "content_type", length = 60)
    private String contentType;

    @Column(name = "size_bytes")
    private Integer sizeBytes;

    /**
     * Lazy, and deliberately so: this is the only column on the table measured in
     * megabytes, and every listing page reads the row without wanting the bytes.
     * Fetching it eagerly would drag the whole catalogue's photographs through
     * memory to render a grid of cards.
     */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data")
    private byte[] data;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    protected ListingImage() {}

    /** An image that lives elsewhere. */
    public ListingImage(String imageUrl, boolean primary, int displayOrder) {
        this.imageUrl = imageUrl;
        this.primary = primary;
        this.displayOrder = displayOrder;
    }

    /** A photograph uploaded by the seller, stored here. */
    public ListingImage(byte[] data, String contentType, boolean primary, int displayOrder) {
        this.data = data;
        this.contentType = contentType;
        this.sizeBytes = data == null ? 0 : data.length;
        this.primary = primary;
        this.displayOrder = displayOrder;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }

    /** Where a browser should ask for this image. */
    public String publicUrl() {
        return imageUrl != null ? imageUrl : "/api/images/" + id;
    }

    public boolean isUploaded() {
        return imageUrl == null;
    }

    public Integer getId() { return id; }
    public Listing getListing() { return listing; }
    void setListing(Listing listing) { this.listing = listing; }
    public String getImageUrl() { return imageUrl; }
    public Boolean getPrimary() { return primary; }
    public Integer getDisplayOrder() { return displayOrder; }
    public String getContentType() { return contentType; }
    public Integer getSizeBytes() { return sizeBytes; }
    public byte[] getData() { return data; }

    public void setPrimary(boolean primary) { this.primary = primary; }
}
