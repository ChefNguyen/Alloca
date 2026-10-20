package com.alloca.auth;

import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class AuthIT {

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
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
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
}
