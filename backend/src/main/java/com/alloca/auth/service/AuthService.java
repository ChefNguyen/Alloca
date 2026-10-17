package com.alloca.auth.service;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.domain.User;
import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;

import lombok.RequiredArgsConstructor;

/**
 * Application service for authentication.
 *
 * <p>TODO (ALC-8, ALC-9): create persistence dependencies, verify active-user
 * credentials with BCrypt, issue access/refresh JWTs, and prevent account enumeration.
 * See docs/specs/rbac-and-security.md and docs/api/openapi.yaml.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    //     this.userRepository = userRepository;
    //     this.passwordEncoder = passwordEncoder;
    // }
    public LoginResponse login(LoginRequest request){
        User user = userRepository.findByUsername(request.username())
                                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())){
            throw new BadCredentialsException("Invalid username or password");
        }

        DepartmentSummaryResponse deptResponse = user.getDepartment() != null 
                                ? new DepartmentSummaryResponse(user.getDepartment().getId(), user.getDepartment().getName())
                                : null;

        UserResponse userResponse = new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullname(),
            user.getEmail(),
            user.getRole(),
            user.isActive(),
            deptResponse,
            user.getCreatedAt()
        );

        return new LoginResponse(
            "mock-access-token",
            "Bearer",
            1800L,
            "mock-refresh-token",
            604800L,
            userResponse
        );
    }
}
