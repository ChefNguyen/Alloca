package com.alloca.booking.dto;

import com.alloca.auth.dto.UserSummaryResponse;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.resource.dto.ResourceResponse;
import java.time.Instant;
import java.util.UUID;

/**
 * OpenAPI {@code Booking} schema representation.
 */
public record BookingResponse(
        UUID id,
        ResourceResponse resource,
        UserSummaryResponse user,
        UserSummaryResponse createdBy,
        UserSummaryResponse cancelledBy,
        Instant startTime,
        Instant endTime,
        BookingStatus status,
        String purpose,
        String cancelReason,
        Instant checkInAt,
        Instant reminderSentAt,
        Instant createdAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                ResourceResponse.from(booking.getResource()),
                UserSummaryResponse.from(booking.getUser()),
                UserSummaryResponse.from(booking.getCreatedBy()),
                UserSummaryResponse.from(booking.getCancelledBy()),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getPurpose(),
                booking.getCancelReason(),
                null, // checkInAt is for Phase 2
                booking.getReminderSentAt(),
                booking.getCreatedAt()
        );
    }
}

