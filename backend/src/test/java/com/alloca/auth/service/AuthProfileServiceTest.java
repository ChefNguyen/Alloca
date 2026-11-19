package com.alloca.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.dto.ChangePasswordRequest;
import com.alloca.auth.dto.ProfileUpdateRequest;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.JwtService;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthProfileServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        authService = new AuthService(userRepository, passwordEncoder, mock(JwtService.class));
    }

    private User activeUser() {
        return new User(
                UUID.randomUUID(),
                "employee1",
                "employee@alloca.test",
                passwordEncoder.encode("Employee@12345"),
                "Employee User",
                Role.EMPLOYEE,
                null
        );
    }

    @Test
    void updateCurrentUserUpdatesOnlyFullNameAndEmail() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(any(), any())).thenReturn(false);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        var updated = authService.updateCurrentUser(
                user.getId(),
                new ProfileUpdateRequest("  Updated Name  ", "New.Email@Alloca.Test")
        );

        assertThat(updated.fullname()).isEqualTo("Updated Name");
        assertThat(updated.email()).isEqualTo("new.email@alloca.test");
        assertThat(updated.role()).isEqualTo(Role.EMPLOYEE);
        assertThat(updated.isActive()).isTrue();
    }

    @Test
    void updateCurrentUserRejectsEmptyBodyBlankNameAndDuplicateEmail() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("taken@alloca.test", user.getId())).thenReturn(true);

        assertThatThrownBy(() -> authService.updateCurrentUser(user.getId(), new ProfileUpdateRequest(null, null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> authService.updateCurrentUser(user.getId(), new ProfileUpdateRequest("   ", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThatThrownBy(() -> authService.updateCurrentUser(user.getId(), new ProfileUpdateRequest(null, "taken@alloca.test")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
                });
    }

    @Test
    void changePasswordIncrementsCredentialsVersion() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.changePassword(user.getId(), new ChangePasswordRequest("Employee@12345", "NewSecret@12345"));

        assertThat(user.getCredentialsVersion()).isEqualTo(2);
        assertThat(passwordEncoder.matches("NewSecret@12345", user.getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordRejectsWrongCurrentAndSamePassword() {
        User user = activeUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(
                user.getId(), new ChangePasswordRequest("WrongPass@123", "NewSecret@12345")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));

        assertThatThrownBy(() -> authService.changePassword(
                user.getId(), new ChangePasswordRequest("Employee@12345", "Employee@12345")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(400));

        assertThat(user.getCredentialsVersion()).isEqualTo(1);
    }
}
