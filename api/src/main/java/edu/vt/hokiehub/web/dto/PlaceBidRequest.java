package edu.vt.hokiehub.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PlaceBidRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "a bid must be more than zero")
        @Digits(integer = 8, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 500, message = "message must be at most 500 characters")
        String message
) {}
