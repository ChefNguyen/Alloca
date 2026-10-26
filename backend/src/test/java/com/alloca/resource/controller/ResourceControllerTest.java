package com.alloca.resource.controller;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.common.api.PageResponse;
import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.dto.ResourceResponse;
import com.alloca.resource.service.ResourceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResourceController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ResourceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResourceService resourceService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passRequestsThroughMockedJwtFilter() throws Exception {
        passThrough(jwtAuthenticationFilter);
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(resourceService.create(any())).thenReturn(new ResourceResponse(
                id, "Projector", ResourceType.EQUIPMENT, 1, ResourceStatus.AVAILABLE, null, null, null,
                new BigDecimal("85.00"), null));

        mvc.perform(post("/api/v1/resources")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projector","type":"EQUIPMENT","capacity":1,"zoneId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/resources/" + id))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
    }

    @Test
    void createWithMissingFieldsReturnsValidationError() throws Exception {
        mvc.perform(post("/api/v1/resources")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
        verifyNoInteractions(resourceService);
    }

    @Test
    void createWithUnknownPropertyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/resources")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Projector","type":"EQUIPMENT","capacity":1,"zoneId":"%s","color":"red"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void deleteConflictExposesConflictingBookingIds() throws Exception {
        UUID id = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_HAS_ONGOING_OR_FUTURE_BOOKINGS,
                "Resource still has ongoing or future CONFIRMED bookings", null,
                Map.of("conflictingBookingIds", List.of(bookingId))))
                .when(resourceService).delete(id);

        mvc.perform(delete("/api/v1/resources/{id}", id)
                        .with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_HAS_ONGOING_OR_FUTURE_BOOKINGS"))
                .andExpect(jsonPath("$.details.conflictingBookingIds[0]").value(bookingId.toString()))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/resources/{id}", UUID.randomUUID())
                        .with(admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateReturnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(resourceService.update(org.mockito.ArgumentMatchers.eq(id), any())).thenReturn(
                new ResourceDetailResponse(id, "Pod A", ResourceType.ROOM, 20, ResourceStatus.AVAILABLE, null,
                        null, null, new BigDecimal("85.00"), null, List.of()));

        mvc.perform(patch("/api/v1/resources/{id}", id)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(20))
                .andExpect(jsonPath("$.schedules.length()").value(0));
    }

    @Test
    void updateWithInvalidCapacityReturnsValidationError() throws Exception {
        mvc.perform(patch("/api/v1/resources/{id}", UUID.randomUUID())
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"));
        verifyNoInteractions(resourceService);
    }

    @Test
    void updateUnknownResourceReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(resourceService.update(org.mockito.ArgumentMatchers.eq(id), any()))
                .thenThrow(ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found"));

        mvc.perform(patch("/api/v1/resources/{id}", id)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateStatusReturnsResource() throws Exception {
        UUID id = UUID.randomUUID();
        when(resourceService.updateStatus(org.mockito.ArgumentMatchers.eq(id), any())).thenReturn(
                new ResourceResponse(id, "Pod A", ResourceType.ROOM, 4, ResourceStatus.SUSPENDED, null, null, null,
                        new BigDecimal("85.00"), null));

        mvc.perform(patch("/api/v1/resources/{id}/status", id)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"maintenance\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void deleteAvailableResourceReturns400() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESOURCE_STATUS_TRANSITION,
                "Must be SUSPENDED before deletion")).when(resourceService).delete(id);

        mvc.perform(delete("/api/v1/resources/{id}", id)
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESOURCE_STATUS_TRANSITION"));
    }

    @Test
    void getReturnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(resourceService.get(id)).thenReturn(
                new ResourceDetailResponse(id, "Pod A", ResourceType.ROOM, 4, ResourceStatus.AVAILABLE, null,
                        null, null, new BigDecimal("85.00"), null, List.of()));

        mvc.perform(get("/api/v1/resources/{id}", id)
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pod A"));
    }

    @Test
    void listPassesFiltersAndDefaultsToNameSort() throws Exception {
        when(resourceService.list(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), new PageResponse.PageMetadata(0, 20, 0, 0, true, true)));

        mvc.perform(get("/api/v1/resources")
                        .with(admin()).param("type", "ROOM").param("minCapacity", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(resourceService).list(isNull(), eq(ResourceType.ROOM), isNull(), isNull(), isNull(), eq(4),
                pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("name").ascending());
    }

    @Test
    void listRejectsOversizedPage() throws Exception {
        mvc.perform(get("/api/v1/resources")
                        .with(admin()).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(resourceService);
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mvc.perform(get("/api/v1/resources")
                        .with(admin()).param("sort", "password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    void unauthenticatedRequestsAreRejectedWith401() throws Exception {
        mvc.perform(get("/api/v1/resources"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(resourceService);
    }

    @Test
    void employeeCanReadResources() throws Exception {
        UUID id = UUID.randomUUID();
        when(resourceService.list(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), new PageResponse.PageMetadata(0, 20, 0, 0, true, true)));
        when(resourceService.get(id)).thenReturn(
                new ResourceDetailResponse(id, "Pod A", ResourceType.ROOM, 4, ResourceStatus.AVAILABLE, null,
                        null, null, new BigDecimal("85.00"), null, List.of()));

        mvc.perform(get("/api/v1/resources").with(employee())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/resources/{id}", id).with(employee())).andExpect(status().isOk());
    }

    @Test
    void employeeCannotMutateResources() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"name\":\"Projector\",\"type\":\"EQUIPMENT\",\"capacity\":1,\"zoneId\":\"%s\"}"
                .formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/resources").with(employee()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch("/api/v1/resources/{id}", id).with(employee())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/resources/{id}/status", id).with(employee())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/resources/{id}", id).with(employee()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }
}
