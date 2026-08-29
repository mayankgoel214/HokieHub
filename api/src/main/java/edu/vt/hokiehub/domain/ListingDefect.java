package edu.vt.hokiehub.domain;

import jakarta.persistence.*;

/**
 * Something wrong with the item, stated by the seller.
 *
 * Its own row rather than a sentence in the description, because severity is
 * what a buyer weighs and prose cannot be weighed.
 */
@Entity
@Table(name = "listing_defects")
public class ListingDefect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, length = 20)
    private DefectSeverity severity = DefectSeverity.MINOR;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    protected ListingDefect() {
    }

    public ListingDefect(String description, DefectSeverity severity, int displayOrder) {
        this.description = description;
        this.severity = severity;
        this.displayOrder = displayOrder;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public DefectSeverity getSeverity() { return severity; }
    public Integer getDisplayOrder() { return displayOrder; }

    void setListing(Listing listing) { this.listing = listing; }
}
