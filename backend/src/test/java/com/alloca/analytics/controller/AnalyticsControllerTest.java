package com.alloca.analytics.controller;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.manager;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.analytics.dto.UtilizationMetric;
import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.analytics.service.AnalyticsService;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.dto.ResourceResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
        UUID resId = UUID.randomUUID();

        ResourceResponse resDto = new ResourceResponse(
                resId, "Meeting Room Beta", ResourceType.ROOM, 8, ResourceStatus.AVAILABLE,
                "Description", null, null, new BigDecimal("85.00"), null);

        UtilizationMetric metric = new UtilizationMetric(resDto, 480, 600, 80.0);
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
                .andExpect(jsonPath("$.metrics[0].scheduledMinutes").value(480))
                .andExpect(jsonPath("$.metrics[0].operatingMinutes").value(600))
                .andExpect(jsonPath("$.metrics[0].utilizationPercent").value(80.0))
                .andExpect(jsonPath("$.metrics[0].resource.name").value("Meeting Room Beta"));
    }

    @Test
    @DisplayName("GET /api/v1/reports/utilization with manager returns 200")
    void testGetReportsUtilization_Manager_Success() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);

        UtilizationReportResponse report = new UtilizationReportResponse(from, to, List.of());
        when(service.getUtilizationReport(any(), eq(from), eq(to), any(), any()))
                .thenReturn(report);

        mvc.perform(get("/api/v1/reports/utilization")
                        .with(manager())
                        .param("from", "2026-10-05")
                        .param("to", "2026-10-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-10-05"))
                .andExpect(jsonPath("$.metrics").isArray());
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
