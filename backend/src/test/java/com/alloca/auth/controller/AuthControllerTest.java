package com.alloca.auth.controller;
import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.api.dto.RefreshTokenRequest;
import com.alloca.auth.api.dto.AccessTokenResponse;
import com.alloca.auth.exception.InvalidTokenException;
import com.alloca.auth.exception.ExpiredTokenException;
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

    @Test
    @DisplayName("POST /api/v1/auth/refresh thành công trả về 200 OK và access token mới")
    void refreshToken_Success_Returns200() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token-at-least-20-chars");
        AccessTokenResponse response = new AccessTokenResponse("new-mock-access-token", "Bearer", 1800L);

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-mock-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh với token không hợp lệ trả về 401 AUTH_TOKEN_INVALID")
    void refreshToken_InvalidToken_Returns401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-refresh-token-at-least-20-chars");

        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new InvalidTokenException("Invalid token signature"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid token signature"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh với token hết hạn trả về 401 AUTH_TOKEN_EXPIRED")
    void refreshToken_ExpiredToken_Returns401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-refresh-token-at-least-20-chars");

        when(authService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new ExpiredTokenException("Refresh token has expired"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Refresh token has expired"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh token rỗng hoặc dưới 20 ký tự trả về 400 VALIDATION_ERROR")
    void refreshToken_ValidationFailure_Returns400() throws Exception {
        // Token chỉ có 5 ký tự (< 20 ký tự theo quy định @Size(min = 20))
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest("short");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
