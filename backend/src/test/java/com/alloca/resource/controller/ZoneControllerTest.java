package com.alloca.resource.controller;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
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

import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.dto.ZoneWriteRequest;
import com.alloca.resource.service.ZoneService;
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

@WebMvcTest(ZoneController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class ZoneControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ZoneService zoneService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passRequestsThroughMockedJwtFilter() throws Exception {
        passThrough(jwtAuthenticationFilter);
    }

    @Test
    void listReturnsZones() throws Exception {
        UUID id = UUID.randomUUID();
        when(zoneService.list(null, null)).thenReturn(
                List.of(new ZoneResponse(id, "West Wing", "Level 3", "Tower A", null, null, null)));

        mvc.perform(get("/api/v1/zones")
                        .with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].floor").value("Level 3"));
    }

    @Test
    void listPassesBuildingAndFloorFilters() throws Exception {
        when(zoneService.list("Tower A", "Level 3")).thenReturn(List.of());

        mvc.perform(get("/api/v1/zones")
                        .with(employee()).param("building", "Tower A").param("floor", "Level 3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        verify(zoneService).list("Tower A", "Level 3");
    }

    @Test
    void listRejectsOversizedFilter() throws Exception {
        mvc.perform(get("/api/v1/zones")
                        .with(employee()).param("floor", "x".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(zoneService);
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/v1/zones"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(zoneService);
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(zoneService.create(new ZoneWriteRequest("West Wing", "Level 3", "Tower A")))
                .thenReturn(new ZoneResponse(id, "West Wing", "Level 3", "Tower A", null, null, null));

        mvc.perform(post("/api/v1/zones").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"West Wing\",\"floor\":\"Level 3\",\"building\":\"Tower A\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/zones/" + id))
                .andExpect(jsonPath("$.name").value("West Wing"));
    }

    @Test
    void createWithBlankFloorReturnsValidationError() throws Exception {
        mvc.perform(post("/api/v1/zones").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"West Wing\",\"floor\":\" \",\"building\":\"Tower A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("floor"));
        verifyNoInteractions(zoneService);
    }

    @Test
    void updateReturnsZone() throws Exception {
        UUID id = UUID.randomUUID();
        when(zoneService.update(id, new ZoneWriteRequest("North Wing", "Level 4", "Tower A")))
                .thenReturn(new ZoneResponse(id, "North Wing", "Level 4", "Tower A", null, null, null));

        mvc.perform(patch("/api/v1/zones/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"North Wing\",\"floor\":\"Level 4\",\"building\":\"Tower A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.floor").value("Level 4"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/zones/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteZoneInUseReturns409WithResourceCount() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.ZONE_IN_USE, "Zone still has resources",
                null, Map.of("resourceCount", 2L))).when(zoneService).delete(id);

        mvc.perform(delete("/api/v1/zones/{id}", id).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ZONE_IN_USE"))
                .andExpect(jsonPath("$.details.resourceCount").value(2));
    }

    @Test
    void writesAreForbiddenForNonAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"name\":\"A\",\"floor\":\"B\",\"building\":\"C\"}";

        mvc.perform(post("/api/v1/zones").with(employee()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch("/api/v1/zones/{id}", id).with(employee()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/zones/{id}", id).with(employee()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(zoneService);
    }
}
