package com.alloca.analytics.dto;

import java.time.LocalDate;

public record BookingTrendPoint(
        LocalDate date,
        int confirmedCount,
        int completedCount,
        int cancelledCount
) {}
