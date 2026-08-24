package edu.vt.hokiehub.web.dto;

import edu.vt.hokiehub.domain.Listing;
import edu.vt.hokiehub.domain.ListingImage;
import edu.vt.hokiehub.domain.ServiceDetail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The API contract, kept separate from the entity so that a mapping change cannot
 * accidentally expose a column, and so lazy associations are resolved deliberately
 * rather than by Jackson touching a proxy mid-serialisation.
 */
public record ListingResponse(
        UUID id,
        SellerSummary seller,
        CategorySummary category,
        String title,
        String description,
        BigDecimal price,
        String condition,
        String listingType,
        String status,
        String location,
        Integer viewsCount,
        ServiceDetails serviceDetails,
        List<Image> images,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt
) {
    public record SellerSummary(String id, String fullName, String email) {}

    public record CategorySummary(Integer id, String name, Integer parentId) {}

    public record ServiceDetails(List<String> subjects, String availability,
                                 BigDecimal hourlyRate, String experienceLevel) {}

    public record Image(Integer id, String imageUrl, Boolean isPrimary, Integer displayOrder) {}

    /** Summary form: no images or service details, for list endpoints. */
    public static ListingResponse summary(Listing l) {
        return build(l, false);
    }

    /** Full form, for a single listing. */
    public static ListingResponse detail(Listing l) {
        return build(l, true);
    }

    private static ListingResponse build(Listing l, boolean withDetails) {
        ServiceDetail sd = withDetails ? l.getServiceDetail() : null;
        return new ListingResponse(
                l.getId(),
                new SellerSummary(l.getSeller().getId(), l.getSeller().getFullName(), l.getSeller().getEmail()),
                new CategorySummary(l.getCategory().getId(), l.getCategory().getName(),
                        l.getCategory().getParent() == null ? null : l.getCategory().getParent().getId()),
                l.getTitle(),
                l.getDescription(),
                l.getPrice(),
                l.getCondition() == null ? null : l.getCondition().value(),
                l.getListingType().value(),
                l.getStatus().value(),
                l.getLocation(),
                l.getViewsCount(),
                sd == null ? null : new ServiceDetails(sd.getSubjects(), sd.getAvailability(),
                        sd.getHourlyRate(), sd.getExperienceLevel()),
                withDetails ? l.getImages().stream().map(ListingResponse::toImage).toList() : List.of(),
                l.getCreatedAt(),
                l.getUpdatedAt(),
                l.getExpiresAt()
        );
    }

    private static Image toImage(ListingImage i) {
        return new Image(i.getId(), i.getImageUrl(), i.getPrimary(), i.getDisplayOrder());
    }
}
