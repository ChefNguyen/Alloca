package com.alloca.booking.controller;

import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.dto.BookingCreateRequest;
import com.alloca.booking.dto.BookingResponse;
import com.alloca.booking.dto.ResourceAvailabilityResponse;
import com.alloca.booking.dto.TimeSlotResponse;
import com.alloca.booking.service.BookingService;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.domain.ResourceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class BookingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passRequestsThroughMockedJwtFilter() throws Exception {
        passThrough(jwtAuthenticationFilter);
    }

    @Test
    @DisplayName("GET /resources/{id}/availability returns 200 with availability data")
    void getAvailabilityReturns200() throws Exception {
        UUID resourceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 2);

        TimeSlotResponse window = new TimeSlotResponse(
                Instant.parse("2026-11-02T01:00:00Z"), Instant.parse("2026-11-02T10:00:00Z"));
        TimeSlotResponse slot = new TimeSlotResponse(
                Instant.parse("2026-11-02T01:00:00Z"), Instant.parse("2026-11-02T01:30:00Z"));

        ResourceAvailabilityResponse response = new ResourceAvailabilityResponse(
                resourceId, date, "Asia/Ho_Chi_Minh", ResourceStatus.AVAILABLE,
                List.of(window), List.of(slot));

        when(bookingService.getResourceAvailability(eq(resourceId), eq(date), eq(30)))
                .thenReturn(response);

        mvc.perform(get("/api/v1/resources/{resourceId}/availability", resourceId)
                        .param("date", "2026-11-02")
                        .with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId").value(resourceId.toString()))
                .andExpect(jsonPath("$.date").value("2026-11-02"))
                .andExpect(jsonPath("$.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.resourceStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.operatingWindows").isArray())
                .andExpect(jsonPath("$.operatingWindows.length()").value(1))
                .andExpect(jsonPath("$.availableSlots").isArray())
                .andExpect(jsonPath("$.availableSlots.length()").value(1));
    }

    @Test
    @DisplayName("GET /resources/{id}/availability returns 401 when unauthenticated")
    void getAvailabilityReturns401WhenUnauthenticated() throws Exception {
        UUID resourceId = UUID.randomUUID();

        mvc.perform(get("/api/v1/resources/{resourceId}/availability", resourceId)
                        .param("date", "2026-11-02"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /resources/{id}/availability returns 404 when resource not found")
    void getAvailabilityReturns404WhenNotFound() throws Exception {
        UUID resourceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 2);

        when(bookingService.getResourceAvailability(eq(resourceId), eq(date), eq(30)))
                .thenThrow(ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + resourceId));

        mvc.perform(get("/api/v1/resources/{resourceId}/availability", resourceId)
                        .param("date", "2026-11-02")
                        .with(employee()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /resources/{id}/availability returns 400 when date parameter is missing")
    void getAvailabilityReturns400WhenDateParamMissing() throws Exception {
        UUID resourceId = UUID.randomUUID();

        mvc.perform(get("/api/v1/resources/{resourceId}/availability", resourceId)
                        .with(employee()))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // ALC-35: Tests for POST /bookings
    // =========================================================================

    @Test
    @DisplayName("POST /bookings returns 201 Created with Location header")
    void createBookingReturns201() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        Instant start = Instant.parse("2026-11-02T02:00:00Z");
        Instant end = Instant.parse("2026-11-02T03:30:00Z");

        BookingResponse response = new BookingResponse(
                bookingId,
                null,
                null,
                null,
                null,
                start,
                end,
                BookingStatus.CONFIRMED,
                "Standup",
                null,
                null,
                null,
                Instant.now()
        );

        when(bookingService.createBooking(any(BookingCreateRequest.class), any(UserPrincipal.class)))
                .thenReturn(response);

        String json = """
            {
                "resourceId": "%s",
                "startTime": "2026-11-02T02:00:00Z",
                "endTime": "2026-11-02T03:30:00Z",
                "purpose": "Standup"
            }
            """.formatted(resourceId);

        mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(employee()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/bookings/" + bookingId))
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("POST /bookings returns 401 Unauthorized when unauthenticated")
    void createBookingReturns401WhenUnauthenticated() throws Exception {
        UUID resourceId = UUID.randomUUID();
        String json = """
            {
                "resourceId": "%s",
                "startTime": "2026-11-02T02:00:00Z",
                "endTime": "2026-11-02T03:30:00Z"
            }
            """.formatted(resourceId);

        mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /bookings returns 400 Bad Request when required fields are missing")
    void createBookingReturns400WhenMissingRequiredFields() throws Exception {
        // Missing startTime and endTime
        String json = """
            {
                "resourceId": "%s"
            }
            """.formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(employee()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /bookings returns 409 Conflict when time conflict occurs")
    void createBookingReturns409WhenTimeConflict() throws Exception {
        UUID resourceId = UUID.randomUUID();
        when(bookingService.createBooking(any(BookingCreateRequest.class), any(UserPrincipal.class)))
                .thenThrow(ApiException.conflict(ErrorCode.BOOKING_TIME_CONFLICT, "Conflict detected"));

        String json = """
            {
                "resourceId": "%s",
                "startTime": "2026-11-02T02:00:00Z",
                "endTime": "2026-11-02T03:30:00Z"
            }
            """.formatted(resourceId);

        mvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(employee()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_TIME_CONFLICT"));
    }
}

