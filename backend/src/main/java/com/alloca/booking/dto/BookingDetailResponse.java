package com.alloca.booking.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/** Flattened Booking fields plus statusHistory, matching OpenAPI BookingDetail. */
public record BookingDetailResponse(@JsonUnwrapped BookingResponse booking,
                                    List<BookingStatusHistoryResponse> statusHistory) {
}
