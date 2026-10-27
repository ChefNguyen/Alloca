package com.alloca.auth.controller;

import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.UserAccessUpdateRequest;
import com.alloca.auth.api.dto.UserCreateRequest;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.api.dto.UserUpdateRequest;
import com.alloca.auth.domain.Role;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.auth.service.UserService;
import com.alloca.common.api.PageResponse;
import com.alloca.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.alloca.support.WebSecurityTestSupport.admin;
import static com.alloca.support.WebSecurityTestSupport.employee;
import static com.alloca.support.WebSecurityTestSupport.manager;
import static com.alloca.support.WebSecurityTestSupport.passThrough;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@DisplayName("ALC-26 & ALC-27: UserController Slice Tests & Security Guards")
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private UserResponse sampleUserResponse;
    private UUID sampleUserId;

    @BeforeEach
    void setUp() throws Exception {
        passThrough(jwtAuthenticationFilter);

        sampleUserId = UUID.randomUUID();
        sampleUserResponse = new UserResponse(
                sampleUserId,
                "nguyen.van.an",
                "Nguyen Van An",
                "an@alloca.com",
                Role.EMPLOYEE,
                true,
                new DepartmentSummaryResponse(UUID.randomUUID(), "Engineering"),
                Instant.now()
        );
    }

    @Nested
    @DisplayName("ALC-26: POST /api/v1/users (Admin Only)")
    class CreateUserEndpoints {

        @Test
        @DisplayName("Anonymous user returns 401 Unauthorized")
        void createUser_Anonymous_Returns401() throws Exception {
            UserCreateRequest request = new UserCreateRequest(
                    "nguyen.an", "an@alloca.com", "password123", "Nguyen An", Role.EMPLOYEE, null, true
            );

            mvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("EMPLOYEE returns 403 Forbidden")
        void createUser_Employee_Returns403() throws Exception {
            UserCreateRequest request = new UserCreateRequest(
                    "nguyen.an", "an@alloca.com", "password123", "Nguyen An", Role.EMPLOYEE, null, true
            );

            mvc.perform(post("/api/v1/users")
                            .with(employee())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("MANAGER returns 403 Forbidden")
        void createUser_Manager_Returns403() throws Exception {
            UserCreateRequest request = new UserCreateRequest(
                    "nguyen.an", "an@alloca.com", "password123", "Nguyen An", Role.EMPLOYEE, null, true
            );

            mvc.perform(post("/api/v1/users")
                            .with(manager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN returns 201 Created with Location header")
        void createUser_Admin_Returns201() throws Exception {
            UserCreateRequest request = new UserCreateRequest(
                    "nguyen.an", "an@alloca.com", "password123", "Nguyen An", Role.EMPLOYEE, null, true
            );

            when(userService.createUser(any())).thenReturn(sampleUserResponse);

            mvc.perform(post("/api/v1/users")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/users/" + sampleUserResponse.id()))
                    .andExpect(jsonPath("$.id").value(sampleUserResponse.id().toString()))
                    .andExpect(jsonPath("$.username").value(sampleUserResponse.username()));
        }

        @Test
        @DisplayName("Validation failure (short password) returns 400 Bad Request")
        void createUser_ValidationFailure_Returns400() throws Exception {
            UserCreateRequest invalidRequest = new UserCreateRequest(
                    "nguyen.an", "an@alloca.com", "short", "Nguyen An", Role.EMPLOYEE, null, true
            );

            mvc.perform(post("/api/v1/users")
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("ALC-26: GET /api/v1/users (Admin & Manager)")
    class ListUsersEndpoints {

        @Test
        @DisplayName("EMPLOYEE returns 403 Forbidden")
        void listUsers_Employee_Returns403() throws Exception {
            mvc.perform(get("/api/v1/users")
                            .with(employee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("MANAGER returns 200 OK")
        void listUsers_Manager_Returns200() throws Exception {
            when(userService.listUsers(any(), any(), any(), any(), any(), any()))
                    .thenReturn(PageResponse.of(Page.empty(), UserResponse::from));

            mvc.perform(get("/api/v1/users")
                            .with(manager()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN returns 200 OK")
        void listUsers_Admin_Returns200() throws Exception {
            when(userService.listUsers(any(), any(), any(), any(), any(), any()))
                    .thenReturn(PageResponse.of(Page.empty(), UserResponse::from));

            mvc.perform(get("/api/v1/users")
                            .with(admin()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("ALC-26: GET /api/v1/users/{userId}")
    class GetUserEndpoints {

        @Test
        @DisplayName("EMPLOYEE returns 403 Forbidden")
        void getUser_Employee_Returns403() throws Exception {
            mvc.perform(get("/api/v1/users/{userId}", sampleUserId)
                            .with(employee()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN returns 200 OK")
        void getUser_Admin_Returns200() throws Exception {
            when(userService.getUser(any(), eq(sampleUserId))).thenReturn(sampleUserResponse);

            mvc.perform(get("/api/v1/users/{userId}", sampleUserId)
                            .with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()))
                    .andExpect(jsonPath("$.username").value(sampleUserResponse.username()));
        }
    }

    @Nested
    @DisplayName("ALC-26: PATCH /api/v1/users/{userId}")
    class UpdateUserEndpoints {

        @Test
        @DisplayName("MANAGER returns 403 Forbidden")
        void updateUser_Manager_Returns403() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest("new@alloca.com", null, null, null);

            mvc.perform(patch("/api/v1/users/{userId}", sampleUserId)
                            .with(manager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN returns 200 OK")
        void updateUser_Admin_Returns200() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest("new@alloca.com", "New Name", null, null);
            when(userService.updateUser(eq(sampleUserId), any())).thenReturn(sampleUserResponse);

            mvc.perform(patch("/api/v1/users/{userId}", sampleUserId)
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()));
        }
    }

    @Nested
    @DisplayName("ALC-26: DELETE /api/v1/users/{userId}")
    class DeactivateUserEndpoints {

        @Test
        @DisplayName("MANAGER returns 403 Forbidden")
        void deactivateUser_Manager_Returns403() throws Exception {
            mvc.perform(delete("/api/v1/users/{userId}", sampleUserId)
                            .with(manager()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN returns 204 No Content")
        void deactivateUser_Admin_Returns204() throws Exception {
            mvc.perform(delete("/api/v1/users/{userId}", sampleUserId)
                            .with(admin()))
                    .andExpect(status().isNoContent());

            verify(userService).deactivateUser(sampleUserId);
        }
    }

    @Nested
    @DisplayName("ALC-27: PATCH /api/v1/users/{userId}/access")
    class UpdateUserAccessEndpoints {

        @Test
        @DisplayName("MANAGER returns 403 Forbidden")
        void updateUserAccess_Manager_Returns403() throws Exception {
            UserAccessUpdateRequest request = new UserAccessUpdateRequest(Role.MANAGER, UUID.randomUUID());

            mvc.perform(patch("/api/v1/users/{userId}/access", sampleUserId)
                            .with(manager())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN returns 200 OK")
        void updateUserAccess_Admin_Returns200() throws Exception {
            UserAccessUpdateRequest request = new UserAccessUpdateRequest(Role.MANAGER, UUID.randomUUID());
            when(userService.updateUserAccess(any(), eq(sampleUserId), any())).thenReturn(sampleUserResponse);

            mvc.perform(patch("/api/v1/users/{userId}/access", sampleUserId)
                            .with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(sampleUserId.toString()));
        }
    }
}
