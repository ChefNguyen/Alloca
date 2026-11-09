package com.alloca.analytics.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationJobTest {

    @Mock
    private AnalyticsService analyticsService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:05:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Spy
    private ZoneId businessZone = ZoneId.of("Asia/Ho_Chi_Minh");

    @InjectMocks
    private AlertEvaluationJob job;

    @Test
    @DisplayName("runDailyEvaluation calls evaluateDailyUtilizationAlerts with current local date")
    void testRunDailyEvaluation() {
        job.runDailyEvaluation();
        verify(analyticsService).evaluateDailyUtilizationAlerts(
                eq(LocalDate.of(2026, 10, 12)),
                eq(Instant.parse("2026-10-12T00:05:00Z")));
    }

    @Test
    @DisplayName("onStartupCatchUp calls evaluateDailyUtilizationAlerts for catch-up")
    void testOnStartupCatchUp() {
        job.onStartupCatchUp();
        verify(analyticsService).evaluateDailyUtilizationAlerts(
                eq(LocalDate.of(2026, 10, 12)),
                eq(Instant.parse("2026-10-12T00:05:00Z")));
    }
}
