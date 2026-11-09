package com.alloca.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record UtilizationReportResponse(
        LocalDate from,
        LocalDate to,
        String timezone,
        List<UtilizationMetric> metrics
) {
    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    public UtilizationReportResponse(LocalDate from, LocalDate to, List<UtilizationMetric> metrics) {
        this(from, to, DEFAULT_TIMEZONE, metrics);
    }
}
