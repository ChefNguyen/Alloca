package com.alloca.auth.controller;

import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.auth.service.AuthService;
import com.alloca.auth.security.RefreshCookie;
import com.alloca.auth.exception.InvalidTokenException;
import com.alloca.auth.exception.ExpiredTokenException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.http.HttpHeaders;
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
 * <p>Cookie lifecycle routes: POST /login, /refresh, /logout (trusted Origin/header required).
 * Protected routes: GET /me (requires valid access token in SecurityContext).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshCookie refreshCookie;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.issue(response.refreshToken(), response.refreshExpiresIn()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refreshToken(
            @CookieValue(name = RefreshCookie.NAME, required = false) String token,
            HttpServletResponse servletResponse) {
        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            if (token == null || token.isBlank()) {
                throw new InvalidTokenException("Refresh cookie is required");
            }
            AccessTokenResponse response = authService.refreshToken(new RefreshTokenRequest(token));
            return ResponseEntity.ok(response);
        } catch (InvalidTokenException | ExpiredTokenException exception) {
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.clear());
            throw exception;
        }
    }

    /** Clears this browser's cookie; copied JWTs are not revoked server-side. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
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
