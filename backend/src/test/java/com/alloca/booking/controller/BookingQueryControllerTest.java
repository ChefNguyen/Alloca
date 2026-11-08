package com.alloca.booking.controller;

import static com.alloca.support.WebSecurityTestSupport.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alloca.auth.security.*;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.dto.*;
import com.alloca.booking.service.BookingQueryService;
import com.alloca.common.dto.PageResponse;
import com.alloca.common.exception.*;
import com.alloca.config.SecurityConfig;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingQueryController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class BookingQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingQueryService service;
    @MockitoBean JwtAuthenticationFilter filter;
    private final UUID id = UUID.randomUUID();
    @BeforeEach void setUp() throws Exception { passThrough(filter); }

    @Test void bindsFiltersAndPagination() throws Exception {
        var filters = new BookingSearchFilter(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), id, id, id, BookingStatus.CONFIRMED);
        when(service.list(any(), eq(filters), eq(2), eq(10), eq("startTime,asc")))
                .thenReturn(new PageResponse<>(List.of(), new PageResponse.PageMetadata(2, 10, 0, 0, false, true)));
        mvc.perform(get("/api/v1/bookings").with(employee()).param("page", "2").param("size", "10")
                .param("sort", "startTime,asc").param("from", Instant.EPOCH.toString()).param("to", Instant.EPOCH.plusSeconds(3600).toString())
                .param("resourceId", id.toString()).param("userId", id.toString()).param("departmentId", id.toString()).param("status", "CONFIRMED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray()).andExpect(jsonPath("$.page.number").value(2));
    }

    @Test void detailMatchesFlatOpenApiAndHistorySupportsSystemActor() throws Exception {
        var booking = new BookingResponse(id, null, null, null, null, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), BookingStatus.COMPLETED, null, null, null, null, Instant.EPOCH);
        var history = List.of(new BookingStatusHistoryResponse(id, BookingStatus.CONFIRMED, BookingStatus.COMPLETED, null, null, Instant.EPOCH));
        when(service.get(eq(id), any())).thenReturn(new BookingDetailResponse(booking, history));
        when(service.history(eq(id), any())).thenReturn(history);
        mvc.perform(get("/api/v1/bookings/{id}", id).with(employee())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.booking").doesNotExist())
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("COMPLETED"));
        mvc.perform(get("/api/v1/bookings/{id}/history", id).with(employee())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changedBy").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/bookings", "/api/v1/bookings/00000000-0000-0000-0000-000000000001", "/api/v1/bookings/00000000-0000-0000-0000-000000000001/history"})
    void allReadRoutesRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"status=INVALID", "userId=bad", "resourceId=bad", "departmentId=bad", "from=no-offset", "to=wrong", "page=abc", "size=abc"})
    void rejectsMalformedQuery(String pair) throws Exception {
        String[] parts = pair.split("=");
        mvc.perform(get("/api/v1/bookings").with(employee()).param(parts[0], parts[1])).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void returnsBusiness403And404() throws Exception {
        when(service.get(eq(id), any())).thenThrow(new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Outside scope"));
        mvc.perform(get("/api/v1/bookings/{id}", id).with(employee())).andExpect(status().isForbidden());
        when(service.history(eq(id), any())).thenThrow(ApiException.notFound(ErrorCode.BOOKING_NOT_FOUND, "Missing"));
        mvc.perform(get("/api/v1/bookings/{id}/history", id).with(employee())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/bookings/not-a-uuid").with(employee())).andExpect(status().isBadRequest());
    }
}
