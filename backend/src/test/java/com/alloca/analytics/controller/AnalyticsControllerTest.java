package com.alloca.analytics.controller;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.manager;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.analytics.domain.AlertStatus;
import com.alloca.analytics.dto.AlertResponse;
import com.alloca.analytics.dto.BookingTrendPoint;
import com.alloca.analytics.dto.BookingTrendReportResponse;
import com.alloca.analytics.dto.UtilizationMetric;
import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.analytics.service.AnalyticsService;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.common.dto.PageResponse;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.dto.ResourceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AnalyticsService service;

    @MockitoBean
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        passThrough(filter);
    }

    @Test
    @DisplayName("GET /api/v1/analytics/utilization with admin returns 200 and matches OpenAPI schema")
    void testGetUtilization_Admin_Success() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);
        UUID resourceId = UUID.randomUUID();

        ResourceResponse res = new ResourceResponse(
                resourceId, "Boardroom West 301", ResourceType.ROOM, 12,
                ResourceStatus.AVAILABLE, "Main Tower 3F", null, null, new BigDecimal("85.00"), null);
        UtilizationMetric metric = new UtilizationMetric(res, 480, 540, 88.89);
        UtilizationReportResponse report = new UtilizationReportResponse(from, to, List.of(metric));

        when(service.getUtilizationReport(any(), eq(from), eq(to), any(), any()))
                .thenReturn(report);

        mvc.perform(get("/api/v1/analytics/utilization")
                        .with(admin())
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-05"))
                .andExpect(jsonPath("$.to").value("2026-10-12"))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.metrics[0].resource.name").value("Boardroom West 301"))
                .andExpect(jsonPath("$.metrics[0].scheduledMinutes").value(480))
                .andExpect(jsonPath("$.metrics[0].operatingMinutes").value(540))
                .andExpect(jsonPath("$.metrics[0].utilizationPercent").value(88.89));
    }

    @Test
    @DisplayName("GET /api/v1/reports/utilization alias works identically")
    void testGetUtilization_AliasRoute() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);
        UtilizationReportResponse report = new UtilizationReportResponse(from, to, Collections.emptyList());

        when(service.getUtilizationReport(any(), eq(from), eq(to), any(), any()))
                .thenReturn(report);

        mvc.perform(get("/api/v1/reports/utilization")
                        .with(admin())
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-12"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/trends with admin returns 200 and points array")
    void testGetTrends_Admin_Success() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 7);
        BookingTrendPoint p1 = new BookingTrendPoint(from, 3, 2, 1);
        BookingTrendPoint p2 = new BookingTrendPoint(from.plusDays(1), 5, 0, 0);
        BookingTrendReportResponse report = new BookingTrendReportResponse(from, to, List.of(p1, p2));

        when(service.getBookingTrends(any(), eq(from), eq(to), any(), any()))
                .thenReturn(report);

        mvc.perform(get("/api/v1/analytics/trends")
                        .with(admin())
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-05"))
                .andExpect(jsonPath("$.to").value("2026-10-07"))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.points[0].date").value("2026-10-05"))
                .andExpect(jsonPath("$.points[0].confirmedCount").value(3))
                .andExpect(jsonPath("$.points[0].completedCount").value(2))
                .andExpect(jsonPath("$.points[0].cancelledCount").value(1))
                .andExpect(jsonPath("$.points[1].date").value("2026-10-06"))
                .andExpect(jsonPath("$.points[1].confirmedCount").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/alerts returns 200 with PageResponse of AlertResponse")
    void testGetAlerts_Admin_Success() throws Exception {
        ResourceResponse res = new ResourceResponse(
                UUID.randomUUID(), "Boardroom A", ResourceType.ROOM, 10,
                ResourceStatus.AVAILABLE, "Zone 1", null, null, new BigDecimal("85.00"), null);
        AlertResponse alert = new AlertResponse(
                UUID.randomUUID(), res, AlertStatus.OPEN,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                new BigDecimal("89.20"), new BigDecimal("85.00"),
                null, null, null, null, Instant.now());
        PageResponse<AlertResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1),
                a -> a);

        when(service.listAlerts(any(), any(), any(), any(), any())).thenReturn(pageResponse);

        mvc.perform(get("/api/v1/alerts")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.items[0].resource.name").value("Boardroom A"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/alerts/{id}/acknowledge with manager returns 200 and updated alert")
    void testAcknowledgeAlert_Manager_Success() throws Exception {
        UUID alertId = UUID.randomUUID();
        ResourceResponse res = new ResourceResponse(
                UUID.randomUUID(), "Boardroom A", ResourceType.ROOM, 10,
                ResourceStatus.AVAILABLE, "Zone 1", null, null, new BigDecimal("85.00"), null);
        AlertResponse acknowledged = new AlertResponse(
                alertId, res, AlertStatus.ACKNOWLEDGED,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12),
                new BigDecimal("89.20"), new BigDecimal("85.00"),
                UUID.randomUUID(), Instant.now(), null, null, Instant.now());

        when(service.acknowledgeAlert(any(), eq(alertId))).thenReturn(acknowledged);

        mvc.perform(post("/api/v1/alerts/{alertId}/acknowledge", alertId)
                        .with(manager())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertId.toString()))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("GET /api/v1/alerts with employee returns 403 Forbidden")
    void testGetAlerts_Employee_Forbidden() throws Exception {
        mvc.perform(get("/api/v1/alerts")
                        .with(employee()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/alerts/{id}/acknowledge with employee returns 403 Forbidden")
    void testAcknowledgeAlert_Employee_Forbidden() throws Exception {
        mvc.perform(post("/api/v1/alerts/{alertId}/acknowledge", UUID.randomUUID())
                        .with(employee())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/trends with employee returns 403 Forbidden")
    void testGetTrends_Employee_Forbidden() throws Exception {
        mvc.perform(get("/api/v1/analytics/trends")
                        .with(employee())
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-07"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/utilization with employee returns 403 Forbidden")
    void testGetUtilization_Employee_Forbidden() throws Exception {
        mvc.perform(get("/api/v1/analytics/utilization")
                        .with(employee())
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-12"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/utilization without authentication returns 401 Unauthorized")
    void testGetUtilization_Unauthenticated_Returns401() throws Exception {
        mvc.perform(get("/api/v1/analytics/utilization")
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-12"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/analytics/utilization missing 'from' returns 400 Bad Request")
    void testGetUtilization_MissingFromParam_Returns400() throws Exception {
        mvc.perform(get("/api/v1/analytics/utilization")
                        .with(admin())
                        .param("to", "2026-10-12"))
                .andExpect(status().isBadRequest());
    }
}
