package com.alloca.booking.dto;

import jakarta.validation.constraints.Size;

/** Optional cancellation reason; the authenticated actor supplies all audit identity. */
public record BookingCancelRequest(@Size(max = 500) String reason) {
}
