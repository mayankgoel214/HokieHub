package edu.vt.hokiehub.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * A comparable the estimate was drawn from. Without these the number is an
 * assertion, so they are shown to the buyer alongside it.
 */
@Entity
@Table(name = "price_check_sources")
public class PriceCheckSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_check_id", nullable = false)
    private PriceCheck priceCheck;

    @Column(nullable = false, length = 400)
    private String title;

    @Column(length = 1000)
    private String url;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 300)
    private String note;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    protected PriceCheckSource() {}

    public PriceCheckSource(String title, String url, BigDecimal price, String note, int order) {
        this.title = title;
        this.url = url;
        this.price = price;
        this.note = note;
        this.displayOrder = order;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public BigDecimal getPrice() { return price; }
    public String getNote() { return note; }
    public Integer getDisplayOrder() { return displayOrder; }

    void setPriceCheck(PriceCheck priceCheck) { this.priceCheck = priceCheck; }
}
