package com.alloca.analytics.controller;

import com.alloca.analytics.service.AnalyticsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for utilization reports and utilization alerts.
 *
 * <p>TODO (ALC-48 to ALC-53): implement report and alert routes in Sprint 3.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }
}
