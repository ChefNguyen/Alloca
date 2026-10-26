package com.alloca.auth.controller;

import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.auth.service.AuthService;
import lombok.RequiredArgsConstructor;

import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.LoginResponse;
import com.alloca.auth.api.dto.AccessTokenResponse;
import com.alloca.auth.api.dto.RefreshTokenRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

/**
 * HTTP boundary for the Auth & RBAC module (ALC-8, ALC-9, ALC-25).
 *
 * <p>Public routes: POST /login, POST /refresh.
 * Protected routes: GET /me (requires valid access token in SecurityContext).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AccessTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ALC-25: Profile endpoint — returns the authenticated user's current profile.
     * userId comes from the JWT-populated UserPrincipal, never from the request body.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        UserResponse response = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(response);
    }
}