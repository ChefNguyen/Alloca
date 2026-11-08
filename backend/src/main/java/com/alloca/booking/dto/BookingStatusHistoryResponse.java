package com.alloca.booking.dto;

import com.alloca.auth.dto.UserSummaryResponse;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.domain.BookingStatusHistory;
import java.time.Instant;
import java.util.UUID;

public record BookingStatusHistoryResponse(UUID id, BookingStatus oldStatus, BookingStatus newStatus,
        UserSummaryResponse changedBy, String reason, Instant changedAt) {
    public static BookingStatusHistoryResponse from(BookingStatusHistory history) {
        return new BookingStatusHistoryResponse(history.getId(), history.getOldStatus(), history.getNewStatus(),
                UserSummaryResponse.from(history.getChangedBy()), history.getReason(), history.getChangedAt());
    }
}
