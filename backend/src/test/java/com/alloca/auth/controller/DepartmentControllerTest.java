package com.alloca.auth.controller;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.manager;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alloca.auth.api.dto.DepartmentResponse;
import com.alloca.auth.api.dto.DepartmentWriteRequest;
import com.alloca.auth.service.DepartmentService;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.config.SecurityConfig;
import java.time.Instant;
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

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(departmentService.create(new DepartmentWriteRequest("Engineering")))
                .thenReturn(new DepartmentResponse(id, "Engineering", Instant.parse("2026-10-19T03:00:00Z")));

        mvc.perform(post("/api/v1/departments").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Engineering\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/departments/" + id));
    }

    @Test
    void createWithBlankNameReturnsValidationError() throws Exception {
        mvc.perform(post("/api/v1/departments").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(departmentService);
    }

    @Test
    void updateReturnsDepartment() throws Exception {
        UUID id = UUID.randomUUID();
        when(departmentService.update(id, new DepartmentWriteRequest("Platform")))
                .thenReturn(new DepartmentResponse(id, "Platform", Instant.parse("2026-10-19T03:00:00Z")));

        mvc.perform(patch("/api/v1/departments/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"));
    }

    @Test
    void deleteDepartmentInUseReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.CONFLICT, ErrorCode.DEPARTMENT_IN_USE, "Department is in use",
                null, Map.of("userCount", 1L, "resourceCount", 0L))).when(departmentService).delete(id);

        mvc.perform(delete("/api/v1/departments/{id}", id).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_IN_USE"))
                .andExpect(jsonPath("$.details.userCount").value(1));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/v1/departments/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void writesAreForbiddenForNonAdmin() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/api/v1/departments").with(manager()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch("/api/v1/departments/{id}", id).with(employee()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/departments/{id}", id).with(employee()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(departmentService);
    }
}
