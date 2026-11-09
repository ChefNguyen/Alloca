package com.alloca.analytics.controller;

import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.analytics.service.AnalyticsService;
import com.alloca.auth.security.UserPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for utilization reports and alerts.
 *
 * <p>Implements ALC-48 (utilization endpoints) and ALC-52 (department scope).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping({"/analytics/utilization", "/reports/utilization"})
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<UtilizationReportResponse> getUtilization(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "resourceId", required = false) UUID resourceId,
            @RequestParam(name = "departmentId", required = false) UUID departmentId) {
        UtilizationReportResponse report =
                analyticsService.getUtilizationReport(principal, from, to, resourceId, departmentId);
        return ResponseEntity.ok(report);
    }
}
