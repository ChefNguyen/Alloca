package com.alloca.auth.controller;

import com.alloca.auth.api.dto.AccessTokenResponse;
import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.api.dto.RefreshTokenRequest;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.domain.Role;
import com.alloca.auth.exception.ExpiredTokenException;
import com.alloca.auth.exception.InvalidTokenException;
import com.alloca.auth.security.JwtAuthenticationEntryPoint;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.RestAccessDeniedHandler;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.auth.service.AuthService;
import com.alloca.common.api.GlobalExceptionHandler;
import com.alloca.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice tests for AuthController (ALC-8, ALC-9, ALC-25).
 *
 * <p>Imports the REAL SecurityConfig (stateless JWT, no session).
 * JwtAuthenticationFilter is @MockBean-ed and configured via @BeforeEach to
 * transparently pass-through all requests (simulating "no token" state).
 * Tests that need an authenticated SecurityContext inject one via the
 * SecurityMockMvcRequestPostProcessors.authentication() post-processor.
 *
 * <p>Root cause note: Mockito mocks ALL methods including doFilter(), so by
 * default the mock filter blocks every request. The doAnswer below restores
 * the normal pass-through behaviour expected by the filter chain.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        JwtAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    /**
     * The real JwtAuthenticationFilter is mocked to avoid needing JwtService
     * and UserRepository in this slice. The @BeforeEach configures it to
     * pass-through so the rest of the security filter chain executes normally.
     */
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void configurePassThroughFilter() throws Exception {
        // Make the mock filter transparent: always delegate to the next filter.
        // Without this, the Mockito mock would intercept every request and return
        // an empty 200 response, breaking all test assertions.
        doAnswer(invocation -> {
            ServletRequest req = invocation.getArgument(0);
            ServletResponse res = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(req, res);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    // ===================================================================
    // ALC-8: Login
    // ===================================================================
    @Nested
    @DisplayName("ALC-8: POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("200 OK — valid credentials return tokens and user info")
        void login_success_Return200() throws Exception {
            LoginRequest request = new LoginRequest("testUser", "password123");
            UserResponse userResponse = new UserResponse(
                    UUID.randomUUID(), "testuser", "Test User", "test@alloca.com",
                    Role.EMPLOYEE, true,
                    new DepartmentSummaryResponse(UUID.randomUUID(), "Engineering"),
                    Instant.now());
            LoginResponse response = new LoginResponse(
                    "mock-access-token", "Bearer", 1800L,
                    "mock-refresh-token", 604800L, userResponse);

            when(authService.login(any(LoginRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user.username").value("testuser"));
        }

        @Test
        @DisplayName("401 AUTH_INVALID_CREDENTIALS — wrong password")
        void login_InvalidCredentials_Returns401() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenThrow(new BadCredentialsException("Invalid username or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("testuser", "wrongPassword"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR — blank username or password too short")
        void login_ValidationFailure_Returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("", "123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }

    // ===================================================================
    // ALC-9: Refresh Token
    // ===================================================================
    @Nested
    @DisplayName("ALC-9: POST /api/v1/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("200 OK — valid refresh token returns new access token")
        void refreshToken_Success_Returns200() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenReturn(new AccessTokenResponse("new-mock-access-token", "Bearer", 1800L));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshTokenRequest("valid-refresh-token-at-least-20-chars"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new-mock-access-token"))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(1800));
        }

        @Test
        @DisplayName("401 AUTH_TOKEN_INVALID — tampered or invalid signature")
        void refreshToken_InvalidToken_Returns401() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenThrow(new InvalidTokenException("Invalid token signature"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshTokenRequest("invalid-refresh-token-at-least-20-chars"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid token signature"));
        }

        @Test
        @DisplayName("401 AUTH_TOKEN_EXPIRED — expired refresh token")
        void refreshToken_ExpiredToken_Returns401() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenThrow(new ExpiredTokenException("Refresh token has expired"));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshTokenRequest("expired-refresh-token-at-least-20-chars"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Refresh token has expired"));
        }

        @Test
        @DisplayName("400 VALIDATION_ERROR — token shorter than 20 chars")
        void refreshToken_ValidationFailure_Returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshTokenRequest("short"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.fieldErrors").isArray());
        }
    }

    // ===================================================================
    // ALC-25: GET /me, 401 guard, public-route accessibility
    // ===================================================================
    @Nested
    @DisplayName("ALC-25: GET /api/v1/auth/me & Security Guards")
    class MeAndSecurity {

        private UserPrincipal buildPrincipal(Role role) {
            return new UserPrincipal(
                    UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    "testuser", role, UUID.fromString("22222222-2222-2222-2222-222222222222"));
        }

        @Test
        @DisplayName("200 OK — authenticated user gets their own profile")
        void getMe_Authenticated_Returns200() throws Exception {
            UserPrincipal principal = buildPrincipal(Role.EMPLOYEE);
            UserResponse userResponse = new UserResponse(
                    principal.getId(), "testuser", "Test User", "test@alloca.com",
                    Role.EMPLOYEE, true,
                    new DepartmentSummaryResponse(principal.getDepartmentId(), "Engineering"),
                    Instant.now());

            when(authService.getCurrentUser(principal.getId())).thenReturn(userResponse);

            // Inject a pre-built authentication token directly into the SecurityContext
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            mockMvc.perform(get("/api/v1/auth/me").with(authentication(auth)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                    .andExpect(jsonPath("$.department.name").value("Engineering"));
        }

        @Test
        @DisplayName("401 AUTH_TOKEN_INVALID — no Authorization header on protected route")
        void getMe_NoToken_Returns401() throws Exception {
            // No authentication header → filter passes through → Spring Security
            // sees empty SecurityContext on a protected route → EntryPoint fires.
            // Note: EntryPoint sets Content-Type directly at servlet level including charset.
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Type", "application/problem+json;charset=UTF-8"))
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                    .andExpect(jsonPath("$.status").value(401));
        }


        @Test
        @DisplayName("Public route — POST /login accessible without token")
        void publicRoute_Login_IsAccessibleWithoutToken() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                    .thenReturn(new LoginResponse("at", "Bearer", 1800L, "rt", 604800L, null));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("user", "password123"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Public route — POST /refresh accessible without token")
        void publicRoute_Refresh_IsAccessibleWithoutToken() throws Exception {
            when(authService.refreshToken(any(RefreshTokenRequest.class)))
                    .thenReturn(new AccessTokenResponse("at", "Bearer", 1800L));

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RefreshTokenRequest("valid-token-at-least-20-chars-here"))))
                    .andExpect(status().isOk());
        }
    }
}
