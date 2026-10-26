package com.alloca.auth.controller;

import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.auth.api.dto.DepartmentResponse;
import com.alloca.auth.service.DepartmentService;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DepartmentController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class DepartmentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DepartmentService departmentService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void passRequestsThroughMockedJwtFilter() throws Exception {
        passThrough(jwtAuthenticationFilter);
    }

    @Test
    void listReturnsDepartments() throws Exception {
        UUID id = UUID.randomUUID();
        when(departmentService.list()).thenReturn(
                List.of(new DepartmentResponse(id, "Engineering", Instant.parse("2026-10-19T03:00:00Z"))));

        mvc.perform(get("/api/v1/departments")
                        .with(employee()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Engineering"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-19T03:00:00Z"));
    }

    @Test
    void unauthenticatedRequestIsRejectedWith401() throws Exception {
        mvc.perform(get("/api/v1/departments"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(departmentService);
    }
}
