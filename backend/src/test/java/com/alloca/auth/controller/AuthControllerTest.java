package com.alloca.auth.controller;
import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.domain.Role;
import com.alloca.auth.service.AuthService;
import com.alloca.common.api.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
public class AuthControllerTest {
    
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/v1/auth/login thành công trả về 200 OK và token")
    void login_success_Return200() throws Exception{
        LoginRequest request = new LoginRequest("testUser", "password123");
        UserResponse userResponse = new UserResponse(
            UUID.randomUUID(),
            "testuser",
            "Test User",
            "test@alloca.com",
            Role.EMPLOYEE,
            true,
            new DepartmentSummaryResponse(UUID.randomUUID(), "Engineering"),
            Instant.now()
        );
        LoginResponse response = new LoginResponse(
            "mock-access-token",
            "Bearer",
            1800L,
            "mock-refresh-token",
            604800L,
            userResponse
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                                                .content(objectMapper.writeValueAsString(request)))
                                                .andExpect(status().isOk())
                                                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                                                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                                                .andExpect(jsonPath("$.user.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login sai thông tin trả về 401 và RFC 7807 ErrorResponse")
    void login_InvalidCredentials_Returns401() throws Exception {
        LoginRequest request = new LoginRequest("testuser", "wrongPassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login thiếu dữ liệu (validation fail) trả về 400 Bad Request")
    void login_ValidationFailure_Returns400() throws Exception {
        // Mật khẩu dưới 8 ký tự và username rỗng (vi phạm validation ở LoginRequest)
        LoginRequest invalidRequest = new LoginRequest("", "123");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
