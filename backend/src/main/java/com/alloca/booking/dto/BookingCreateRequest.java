package com.alloca.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for {@code POST /api/v1/bookings}.
 */
public record BookingCreateRequest(
        @NotNull(message = "resourceId must not be null")
        UUID resourceId,

        UUID userId,

        @NotNull(message = "startTime must not be null")
        Instant startTime,

        @NotNull(message = "endTime must not be null")
        Instant endTime,

        @Size(max = 500, message = "purpose must not exceed 500 characters")
        String purpose
) {
}

