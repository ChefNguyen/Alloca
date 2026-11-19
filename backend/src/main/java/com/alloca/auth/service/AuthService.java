package com.alloca.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alloca.auth.security.JwtService;
import com.alloca.auth.dto.LoginResponse;
import com.alloca.auth.dto.DepartmentSummaryResponse;
import com.alloca.auth.dto.UserResponse;
import com.alloca.auth.domain.User;
import com.alloca.auth.dto.ChangePasswordRequest;
import com.alloca.auth.dto.LoginRequest;
import com.alloca.auth.dto.AccessTokenResponse;
import com.alloca.auth.dto.ProfileUpdateRequest;
import com.alloca.auth.dto.RefreshTokenRequest;
import com.alloca.auth.repository.UserRepository;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;

import java.text.ParseException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * ALC-25: Trả về thông tin profile của người dùng đang đăng nhập.
     * userId được trích xuất từ UserPrincipal trong SecurityContext — không thể giả mạo.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user no longer exists"));

        return toUserResponse(user);
    }

    /**
     * ALC-28: Cập nhật hồ sơ cá nhân (chỉ cho phép fullName và email; không thể tự đổi role/department/isActive).
     */
    @Transactional
    public UserResponse updateCurrentUser(UUID userId, ProfileUpdateRequest request) {
        if (request.fullName() == null && request.email() == null) {
            throw ApiException.invalidField("body", "At least one of fullName or email is required");
        }
        String fullName = request.fullName() == null ? null : request.fullName().trim();
        if (fullName != null && fullName.isEmpty()) {
            throw ApiException.invalidField("fullName", "Full name must not be blank");
        }
        String email = request.email() == null ? null : request.email().trim().toLowerCase();

        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user no longer exists"));

        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already in use");
        }
        if (fullName != null) {
            user.setFullname(fullName);
        }
        if (email != null) {
            user.setEmail(email);
        }
        user.setUpdatedAt(Instant.now());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already in use");
        }
        return toUserResponse(user);
    }

    /**
     * ALC-28: Đổi mật khẩu của người dùng hiện tại và tăng credentials_version để vô hiệu refresh token cũ.
     */
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidTokenException("Authenticated user no longer exists"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.invalidField("currentPassword", "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw ApiException.invalidField("newPassword", "New password must differ from current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.incrementCredentialsVersion();
        userRepository.save(user);
    }

    private static UserResponse toUserResponse(User user) {
        DepartmentSummaryResponse deptResponse = user.getDepartment() != null
                ? new DepartmentSummaryResponse(user.getDepartment().getId(), user.getDepartment().getName())
                : null;

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullname(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                deptResponse,
                user.getCreatedAt());
    }
}
