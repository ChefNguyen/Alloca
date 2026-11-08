package com.alloca.booking.dto;

import com.alloca.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.UUID;

/** Optional filters intersect the actor's authorized scope. Time range uses overlap [from,to). */
public record BookingSearchFilter(Instant from, Instant to, UUID resourceId, UUID userId,
                                  UUID departmentId, BookingStatus status) {
}
