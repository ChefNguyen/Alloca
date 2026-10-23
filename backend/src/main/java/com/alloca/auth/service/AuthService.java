package com.alloca.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alloca.auth.security.JwtService;
import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.api.dto.DepartmentSummaryResponse;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.domain.User;
import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.AccessTokenResponse;
import com.alloca.auth.api.dto.RefreshTokenRequest;
import com.alloca.auth.repository.UserRepository;

import java.text.ParseException;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import com.alloca.auth.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;

/**
 * Application service for authentication.
 *
 * <p>
 * TODO (ALC-8, ALC-9): create persistence dependencies, verify active-user
 * credentials with BCrypt, issue access/refresh JWTs, and prevent account
 * enumeration.
 * See docs/specs/rbac-and-security.md and docs/api/openapi.yaml.
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // public AuthService(UserRepository userRepository, PasswordEncoder
    // passwordEncoder) {
    // this.userRepository = userRepository;
    // this.passwordEncoder = passwordEncoder;
    // }
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
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
                user.getCreatedAt());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                refreshToken,
                jwtService.getRefreshTokenTtlSeconds(),
                userResponse);
    }

    @Transactional(readOnly = true)
    public AccessTokenResponse refreshToken(RefreshTokenRequest request) {
        // 1. Parse & kiểm tra tính hợp lệ của token (chữ ký, hạn sử dụng, token_type)
        var claims = jwtService.parseAndValidateRefreshToken(request.refreshToken());

        // 2.Trích xuất userId từ claim "sub"
        UUID userId;
        try{
                userId = UUID.fromString(claims.getSubject());
        }
        catch (IllegalArgumentException | NullPointerException e){
                throw new InvalidTokenException("Invalid token subject: not a valid UUID");
        }

        // 3. Tìm user trong DB và kiểm tra tài khoản có đang active không
        User user = userRepository.findById(userId).orElseThrow(() -> new InvalidTokenException("User not found or deleted"));

        if (!user.isActive()){
                throw new InvalidTokenException("User account is inactive or disabled");
        }

        // 4. So khớp credentials_version chống dùng refresh token cũ sau khi đã đổi mật khẩu)
        Integer tokenCredentialsVersion;
        try{
                tokenCredentialsVersion = claims.getIntegerClaim("credentials_version");
        }
        catch (ParseException e){
                throw new InvalidTokenException("Malformed credentials_Version");
        }

        if (tokenCredentialsVersion == null || tokenCredentialsVersion != user.getCredentialsVersion()) {
                throw new InvalidTokenException("Token credentials version mismatch: password was changed or sessions revoke");
        }

        // 5. Cấp Access Token mới
        String newAccessToken = jwtService.generateAccessToken(user);

        return new AccessTokenResponse(
                newAccessToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );
    }
}
