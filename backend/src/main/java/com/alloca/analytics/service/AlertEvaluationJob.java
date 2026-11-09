package com.alloca.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily scheduled evaluation job for booking utilization alerts (FR-STAT-03, TC-JOB-04, TC-STAT-04/05).
 * Runs at 00:05 in Asia/Ho_Chi_Minh and catches up on startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEvaluationJob {

    private final AnalyticsService analyticsService;
    private final Clock clock;
    private final ZoneId businessZone;

    @Scheduled(cron = "${alloca.alerts.cron:0 5 0 * * ?}", zone = "Asia/Ho_Chi_Minh")
    public void runDailyEvaluation() {
        log.info("Running scheduled daily utilization alert evaluation");
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZone);
        analyticsService.evaluateDailyUtilizationAlerts(today, now);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCatchUp() {
        log.info("Running startup catch-up utilization alert evaluation");
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, businessZone);
        analyticsService.evaluateDailyUtilizationAlerts(today, now);
    }
}
