package com.alloca.resource.controller;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.service.ResourceScheduleService;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResourceScheduleController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ResourceScheduleControllerTest {

    private static final String BODY = """
            {"schedules":[{"dayOfWeek":"MONDAY","startTime":"08:00","endTime":"17:00:30"}]}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResourceScheduleService scheduleService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passRequestsThroughMockedJwtFilter() throws Exception {
        passThrough(jwtAuthenticationFilter);
    }

    private static ResourceDetailResponse.Schedule schedule() {
        return new ResourceDetailResponse.Schedule(UUID.randomUUID(), DayOfWeek.MONDAY, "08:00:00", "17:00:30");
    }

    @Test
    void adminReplacesTheSchedule() throws Exception {
        UUID id = UUID.randomUUID();
        when(scheduleService.replace(eq(id), any())).thenReturn(List.of(schedule()));

        mvc.perform(put("/api/v1/resources/{id}/schedules", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].startTime").value("08:00:00"));
    }

    @Test
    void conflictExposesTheConflictingBookingIds() throws Exception {
        UUID id = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(scheduleService.replace(eq(id), any())).thenThrow(new ApiException(HttpStatus.CONFLICT,
                ErrorCode.RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS, "conflict", null,
                Map.of("conflictingBookingIds", List.of(bookingId))));

        mvc.perform(put("/api/v1/resources/{id}/schedules", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS"))
                .andExpect(jsonPath("$.details.conflictingBookingIds[0]").value(bookingId.toString()));
    }

    @Test
    void unknownResourceIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(scheduleService.get(id)).thenThrow(ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "missing"));

        mvc.perform(get("/api/v1/resources/{id}/schedules", id).with(employee()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void employeeCanReadButNotReplace() throws Exception {
        UUID id = UUID.randomUUID();
        when(scheduleService.get(id)).thenReturn(List.of(schedule()));

        mvc.perform(get("/api/v1/resources/{id}/schedules", id).with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/v1/resources/{id}/schedules", id).with(employee())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/v1/resources/{id}/schedules", id)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/resources/{id}/schedules", id)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(scheduleService);
    }

    @Test
    void malformedBodiesAreRejectedBeforeReachingTheService() throws Exception {
        UUID id = UUID.randomUUID();
        String tooMany = "{\"schedules\":[" + String.join(",",
                java.util.Collections.nCopies(22,
                        "{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\",\"endTime\":\"09:00\"}")) + "]}";
        List<String> invalidBodies = List.of(
                "{\"schedules\":[]}",
                "{}",
                tooMany,
                "{\"schedules\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"8am\",\"endTime\":\"17:00\"}]}",
                "{\"schedules\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"24:00\",\"endTime\":\"17:00\"}]}",
                "{\"schedules\":[{\"dayOfWeek\":\"FUNDAY\",\"startTime\":\"08:00\",\"endTime\":\"17:00\"}]}",
                "{\"schedules\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\"}]}",
                "{\"schedules\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"08:00\",\"endTime\":\"17:00\",\"x\":1}]}");

        for (String body : invalidBodies) {
            mvc.perform(put("/api/v1/resources/{id}/schedules", id).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(scheduleService);
    }

    @Test
    void invalidTimeNamesTheOffendingField() throws Exception {
        mvc.perform(put("/api/v1/resources/{id}/schedules", UUID.randomUUID()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schedules\":[{\"dayOfWeek\":\"MONDAY\",\"startTime\":\"8am\",\"endTime\":\"17:00\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("schedules[0].startTime"));
    }
}
