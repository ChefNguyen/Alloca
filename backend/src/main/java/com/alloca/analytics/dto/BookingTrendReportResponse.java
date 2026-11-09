package com.alloca.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record BookingTrendReportResponse(
        LocalDate from,
        LocalDate to,
        String timezone,
        List<BookingTrendPoint> points
) {
    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    public BookingTrendReportResponse(LocalDate from, LocalDate to, List<BookingTrendPoint> points) {
        this(from, to, DEFAULT_TIMEZONE, points);
    }
}
