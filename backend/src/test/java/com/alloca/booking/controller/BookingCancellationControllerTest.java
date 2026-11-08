package com.alloca.booking.controller;

import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alloca.auth.domain.Role;
import com.alloca.auth.security.*;
import com.alloca.booking.dto.BookingCancelRequest;
import com.alloca.booking.service.BookingService;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class BookingCancellationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BookingService service;
    @MockitoBean JwtAuthenticationFilter jwtFilter;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception { passThrough(jwtFilter); }

    @ParameterizedTest
    @EnumSource(Role.class)
    void routesAllAuthenticatedRolesToServiceWithAuthenticatedIdentity(Role role) throws Exception {
        UserPrincipal actor = new UserPrincipal(UUID.randomUUID(), "actor", role, null);
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(user(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed plans\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).cancelBooking(id, new BookingCancelRequest("Changed plans"), actor);
    }

    @Test
    void bodyIsOptional() throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(employee()))
                .andExpect(status().isNoContent());
        verify(service).cancelBooking(eq(id), isNull(), any(UserPrincipal.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\":null}", "{\"reason\":\"\"}"})
    void emptyReasonIsOptional(String body) throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(employee())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
    }

    @Test
    void allowsReasonAtMaxLength() throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(employee())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "a".repeat(500) + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsOversizedReason() throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(employee())
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"cancelledBy\":\"forged\"}", "{\"departmentId\":\"forged\"}", "[]"})
    void rejectsMalformedOrForgedBody(String body) throws Exception {
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(employee())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedIdAndUnauthenticatedRequest() throws Exception {
        mvc.perform(post("/api/v1/bookings/not-a-uuid/cancel").with(employee())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"FORBIDDEN", "NOT_FOUND", "CONFLICT"})
    void exposesBusinessFailures(HttpStatus status) throws Exception {
        ErrorCode code = switch (status) {
            case FORBIDDEN -> ErrorCode.ACCESS_DENIED;
            case NOT_FOUND -> ErrorCode.BOOKING_NOT_FOUND;
            default -> ErrorCode.BOOKING_CANNOT_BE_CANCELLED;
        };
        doThrow(new ApiException(status, code, "Rejected")).when(service).cancelBooking(eq(id), isNull(), any());
        mvc.perform(post("/api/v1/bookings/{id}/cancel", id).with(employee()).param("departmentId", UUID.randomUUID().toString()))
                .andExpect(status().is(status.value())).andExpect(jsonPath("$.code").value(code.name()));
    }
}
