package edu.vt.hokiehub.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.util.List;

/** Extra fields that only apply when a listing is a service (tutoring and similar). */
@Entity
@Table(name = "service_details")
public class ServiceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, unique = true)
    private Listing listing;

    /** Stored as a native Postgres text[] rather than a join table. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> subjects;

    @Column(columnDefinition = "text")
    private String availability;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    protected ServiceDetail() {}

    public ServiceDetail(List<String> subjects, String availability,
                         BigDecimal hourlyRate, String experienceLevel) {
        this.subjects = subjects;
        this.availability = availability;
        this.hourlyRate = hourlyRate;
        this.experienceLevel = experienceLevel;
    }

    public Integer getId() { return id; }
    public Listing getListing() { return listing; }
    void setListing(Listing listing) { this.listing = listing; }
    public List<String> getSubjects() { return subjects; }
    public String getAvailability() { return availability; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public String getExperienceLevel() { return experienceLevel; }
}
