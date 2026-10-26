package com.alloca.resource.controller;

import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.config.SecurityConfig;
import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.service.ZoneService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
}
