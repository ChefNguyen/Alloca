package com.alloca.auth.service;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.JwtService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test; 
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private Department department;

    @BeforeEach
    void setup(){
        department = new Department(UUID.randomUUID(), "Engineering");
        activeUser = new User(
            UUID.randomUUID(),
            "testuser",
            "test@alloca.com",
            "$2a$12$hashedPasswordExample",
            "Test User",
            Role.EMPLOYEE,
            department
        );
    }

    @Test
    @DisplayName("Login thành công khi thông tin đăng nhập chính xác")
    void login_success(){
        // ARRANGE
        LoginRequest request = new LoginRequest("testuser", "correctPassword123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("correctPassword123", activeUser.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(activeUser)).thenReturn("valid-access-token");
        when(jwtService.generateRefreshToken(activeUser)).thenReturn("valid-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(604800L);

        // ACT
        LoginResponse response = authService.login(request);

        // ASSERT
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("valid-access-token");
        assertThat(response.refreshToken()).isEqualTo("valid-refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().username()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Ném BadCredentialsException khi tài khoản bị vô hiệu hóa (is_active = false)")
    void login_InactiveUser_ThrowsException(){
        activeUser.deactivate();
        LoginRequest request = new LoginRequest("testuser", "correctPassword123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        
        assertThatThrownBy( () -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("Ném BadCredentialsException khi sai mật khẩu")
    void login_WrongPassword_ThrowsException(){
        LoginRequest request = new LoginRequest("testuser", "wrongPassword123");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("wrongPassword123", activeUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("Ném BadCredentialsException khi không tìm thấy username")
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest("unknownuser", "password123");

        when(userRepository.findByUsername("unknownuser")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
    }

}
