package com.alloca.auth;

import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.RefreshTokenRequest;
import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import jakarta.servlet.http.Cookie;
import com.alloca.auth.security.RefreshCookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "CORS_ALLOWED_ORIGINS=http://localhost:5173", "AUTH_COOKIE_SECURE=true", "AUTH_COOKIE_SAME_SITE=Lax",
        "JWT_SECRET=integration-test-only-secret-with-at-least-32-bytes"})
@AutoConfigureMockMvc
@Testcontainers
class AuthIT {
    private static MockHttpServletRequestBuilder post(String path) {
        return MockMvcRequestBuilders.post(path)
                .header("Origin", "http://localhost:5173").header("X-Requested-With", "Alloca");
    }


    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Department department;

    @BeforeEach
    void setUp() {
        // Dọn dẹp dữ liệu trước mỗi bài test để đảm bảo tính độc lập (Test Isolation)
        userRepository.deleteAll();
        departmentRepository.deleteAll();

        department = new Department(UUID.randomUUID(), "Engineering");
        departmentRepository.save(department);
    }

    @Test
    @DisplayName("IT: Đăng nhập thành công với tài khoản thật trong PostgreSQL -> Trả về 200 OK và JWT Token thật")
    void login_Success_WithRealDatabase() throws Exception {
        // 1. Lưu User thật vào Database PostgreSQL với mật khẩu băm BCrypt thật
        String rawPassword = "SecurePassword123!";
        User user = new User(
                UUID.randomUUID(),
                "realuser",
                "realuser@alloca.com",
                passwordEncoder.encode(rawPassword),
                "Real User",
                Role.EMPLOYEE,
                department
        );
        userRepository.save(user);

        // 2. Gửi request đăng nhập
        LoginRequest request = new LoginRequest("realuser", rawPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.user.username").value("realuser"))
                .andExpect(jsonPath("$.user.department.name").value("Engineering"));
    }

    @Test
    @DisplayName("IT: Đăng nhập sai mật khẩu với tài khoản trong PostgreSQL -> Trả về 401 AUTH_INVALID_CREDENTIALS")
    void login_WrongPassword_WithRealDatabase() throws Exception {
        User user = new User(
                UUID.randomUUID(),
                "realuser",
                "realuser@alloca.com",
                passwordEncoder.encode("CorrectPassword123!"),
                "Real User",
                Role.EMPLOYEE,
                department
        );
        userRepository.save(user);

        LoginRequest request = new LoginRequest("realuser", "WrongPassword999!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("IT: Đăng nhập tài khoản bị vô hiệu hóa (is_active = false) trong PostgreSQL -> Trả về 401")
    void login_InactiveAccount_WithRealDatabase() throws Exception {
        User user = new User(
                UUID.randomUUID(),
                "inactiveuser",
                "inactive@alloca.com",
                passwordEncoder.encode("Password123!"),
                "Inactive User",
                Role.EMPLOYEE,
                department
        );
        user.deactivate(); // is_active = false
        userRepository.save(user);

        LoginRequest request = new LoginRequest("inactiveuser", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("IT: Refresh token thật hợp lệ -> Nhận được Access Token mới (200 OK)")
    void refreshToken_Success_WithRealDatabase() throws Exception {
        String rawPassword = "SecurePassword123!";
        User user = new User(
                UUID.randomUUID(),
                "refreshtestuser",
                "refreshtestuser@alloca.com",
                passwordEncoder.encode(rawPassword),
                "Refresh Test User",
                Role.EMPLOYEE,
                department
        );
        userRepository.save(user);

        // 1. Đăng nhập để nhận refresh token thật từ server
        var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("refreshtestuser", rawPassword))))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String refreshToken = loginResponse.getCookie(RefreshCookie.NAME).getValue();
        assertThat(refreshToken).isNotBlank();

        // 2. Gửi request refresh với token thật
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, refreshRequest.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());
    }

    @Test
    @DisplayName("IT: Refresh token không hợp lệ (sai chữ ký / format) -> Trả về 401 AUTH_TOKEN_INVALID")
    void refreshToken_InvalidToken_WithRealDatabase() throws Exception {
        RefreshTokenRequest invalidRequest = new RefreshTokenRequest("this-is-an-invalid-tampered-token-1234567890");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, invalidRequest.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("IT: Đổi mật khẩu (tăng credentials_version) làm vô hiệu hóa refresh token cũ -> Trả về 401 AUTH_TOKEN_INVALID")
    void refreshToken_PasswordChanged_InvalidatesOldRefreshToken() throws Exception {
        String rawPassword = "InitialPassword123!";
        User user = new User(
                UUID.randomUUID(),
                "versiontestuser",
                "versiontestuser@alloca.com",
                passwordEncoder.encode(rawPassword),
                "Version Test User",
                Role.EMPLOYEE,
                department
        );
        userRepository.save(user);

        // 1. Đăng nhập để lấy refresh token mang version 1
        var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("versiontestuser", rawPassword))))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String oldRefreshToken = loginResponse.getCookie(RefreshCookie.NAME).getValue();

        // 2. Mô phỏng đổi mật khẩu: tăng credentials_version trong DB lên 2
        user.incrementCredentialsVersion();
        userRepository.save(user);

        // 3. Dùng lại refresh token cũ mang version 1 -> Server phải từ chối ngay lập tức
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(oldRefreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(RefreshCookie.NAME, refreshRequest.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.status").value(401));
    }

    // ===================================================================
    // ALC-25: JWT Filter, /me endpoint, BR-AUTH-01 active check
    // ===================================================================

    @Test
    @DisplayName("IT ALC-25: GET /auth/me with real access token -> 200 OK with user profile")
    void getMe_WithValidAccessToken_Returns200() throws Exception {
        String rawPassword = "SecurePassword123!";
        User user = new User(UUID.randomUUID(), "meuser", "meuser@alloca.com",
                passwordEncoder.encode(rawPassword), "Me User", Role.EMPLOYEE, department);
        userRepository.save(user);

        // Step 1: Login to get real access token
        String loginResponseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("meuser", rawPassword))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = com.jayway.jsonpath.JsonPath.read(loginResponseJson, "$.accessToken");
        assertThat(accessToken).isNotBlank();

        // Step 2: Call /me with the real access token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("meuser"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.department.name").value("Engineering"));
    }

    @Test
    @DisplayName("IT ALC-25: GET /auth/me without token -> 401 AUTH_TOKEN_INVALID (filter chain guard)")
    void getMe_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("IT ALC-25: GET /auth/me with tampered token -> 401 AUTH_TOKEN_INVALID")
    void getMe_TamperedToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.tampered.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("IT ALC-25 BR-AUTH-01: Deactivated user is rejected even with valid (unexpired) access token -> 401")
    void getMe_DeactivatedUser_WithValidToken_Returns401() throws Exception {
        String rawPassword = "SecurePassword123!";
        User user = new User(UUID.randomUUID(), "deactivateuser", "deactivate@alloca.com",
                passwordEncoder.encode(rawPassword), "Deactivate User", Role.EMPLOYEE, department);
        userRepository.save(user);

        // Step 1: Login to get valid access token while account is still active
        String loginResponseJson = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("deactivateuser", rawPassword))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String accessToken = com.jayway.jsonpath.JsonPath.read(loginResponseJson, "$.accessToken");

        // Step 2: Deactivate the user in DB (simulates admin disabling account)
        user.deactivate();
        userRepository.save(user);

        // Step 3: Try to call /me with the still-valid (unexpired) token — must be rejected
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("IT ALC-25: Token Substitution Attack — refresh token rejected on /auth/me -> 401")
    void getMe_WithRefreshTokenInsteadOfAccessToken_Returns401() throws Exception {
        String rawPassword = "SecurePassword123!";
        User user = new User(UUID.randomUUID(), "confuseuser", "confuse@alloca.com",
                passwordEncoder.encode(rawPassword), "Confuse User", Role.EMPLOYEE, department);
        userRepository.save(user);

        // Login to get REFRESH token
        var loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("confuseuser", rawPassword))))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String refreshToken = loginResponse.getCookie(RefreshCookie.NAME).getValue();

        // Attempt to use the REFRESH token on the /me endpoint (token_type = "refresh_token" != "access_token")
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }
}

