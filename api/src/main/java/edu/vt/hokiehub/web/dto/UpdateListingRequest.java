package edu.vt.hokiehub.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Every field is optional: absent means "leave unchanged". */
public record UpdateListingRequest(
        Integer categoryId,

        @Size(max = 255)
        String title,

        String description,

        @DecimalMin(value = "0.0", message = "price cannot be negative")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price,

        @Pattern(regexp = "new|like_new|good|fair|poor", message = "invalid condition")
        String condition,

        @Pattern(regexp = "available|pending|sold|unavailable", message = "invalid status")
        String status,

        @Size(max = 255)
        String location,

        Instant expiresAt,

        /**
         * Absent leaves the disclosures alone; an empty list clears them. Those have
         * to stay distinguishable, or editing the price would quietly erase what the
         * seller had said was wrong with the item.
         */
        @Valid
        @Size(max = 10, message = "at most 10 defects can be listed")
        List<CreateListingRequest.DefectPayload> defects
) {}
