package edu.vt.hokiehub.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Validation lives on the request boundary rather than in the service, so a bad
 * payload is rejected before any database work happens.
 */
public record CreateListingRequest(
        @NotNull(message = "categoryId is required")
        Integer categoryId,

        @NotBlank(message = "title is required")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        @NotBlank(message = "description is required")
        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", message = "price cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        @Pattern(regexp = "new|like_new|good|fair|poor", message = "invalid condition")
        String condition,

        @NotNull(message = "listingType is required")
        @Pattern(regexp = "item|service", message = "listingType must be 'item' or 'service'")
        String listingType,

        @Size(max = 255)
        String location,

        Instant expiresAt,

        @Valid ServiceDetailPayload serviceDetails,

        @Valid List<ImagePayload> images,

        /** What is wrong with it, said up front rather than discovered on arrival. */
        @Valid
        @Size(max = 10, message = "at most 10 defects can be listed")
        List<DefectPayload> defects
) {
    public record DefectPayload(
            @NotBlank(message = "a defect needs a description")
            @Size(max = 200, message = "a defect description must be at most 200 characters")
            String description,

            @Pattern(regexp = "minor|moderate|major", message = "severity must be minor, moderate or major")
            String severity
    ) {}

    public record ServiceDetailPayload(
            List<String> subjects,
            String availability,
            @DecimalMin(value = "0.0", message = "hourlyRate cannot be negative")
            BigDecimal hourlyRate,
            String experienceLevel
    ) {}

    public record ImagePayload(
            @NotBlank(message = "imageUrl is required")
            @Size(max = 500)
            String imageUrl,
            Boolean isPrimary,
            Integer displayOrder
    ) {}
}
