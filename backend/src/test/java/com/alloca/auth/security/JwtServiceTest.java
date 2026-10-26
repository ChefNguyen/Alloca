package com.alloca.auth.security;

import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.exception.ExpiredTokenException;
import com.alloca.auth.exception.InvalidTokenException;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ALC-25: JwtService Unit Tests")
class JwtServiceTest {

    private static final String SECRET = "this-is-a-very-secure-secret-key-that-is-at-least-256-bits-long!";
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        // TTL: 1800s access token, 7 days refresh token
        jwtService = new JwtService(SECRET, 1800L, 7L);

        Department dept = new Department(UUID.randomUUID(), "Engineering");
        user = new User(UUID.randomUUID(), "testuser", "test@alloca.com", "hash", "Test User", Role.EMPLOYEE, dept);
    }

    @Test
    @DisplayName("Generate and parse valid Access Token -> success with matching claims")
    void generateAndValidateAccessToken_Success() throws Exception {
        String token = jwtService.generateAccessToken(user);

        JWTClaimsSet claims = jwtService.parseAndValidateAccessToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("role")).isEqualTo("EMPLOYEE");
        assertThat(claims.getStringClaim("token_type")).isEqualTo("access_token");
        assertThat(claims.getStringClaim("department")).isEqualTo(user.getDepartment().getId().toString());
    }

    @Test
    @DisplayName("Generate and parse valid Refresh Token -> success with matching claims")
    void generateAndValidateRefreshToken_Success() throws Exception {
        String token = jwtService.generateRefreshToken(user);

        JWTClaimsSet claims = jwtService.parseAndValidateRefreshToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.getStringClaim("token_type")).isEqualTo("refresh_token");
        assertThat(claims.getIntegerClaim("credentials_version")).isEqualTo(user.getCredentialsVersion());
    }

    @Test
    @DisplayName("Tampered signature throws InvalidTokenException")
    void validateToken_TamperedSignature_ThrowsInvalidTokenException() {
        String validToken = jwtService.generateAccessToken(user);
        // Tamper signature
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(tamperedToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Expired Access Token throws ExpiredTokenException")
    void validateAccessToken_Expired_ThrowsExpiredTokenException() {
        // Service with negative TTL (already expired)
        JwtService expiredJwtService = new JwtService(SECRET, -10L, 7L);
        String token = expiredJwtService.generateAccessToken(user);

        assertThatThrownBy(() -> expiredJwtService.parseAndValidateAccessToken(token))
                .isInstanceOf(ExpiredTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Token Substitution Attack: Refresh token rejected when validated as Access Token")
    void validateAccessToken_WithRefreshToken_ThrowsInvalidTokenException() {
        String refreshToken = jwtService.generateRefreshToken(user);

        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken(refreshToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expected access_token");
    }

    @Test
    @DisplayName("Token Substitution Attack: Access token rejected when validated as Refresh Token")
    void validateRefreshToken_WithAccessToken_ThrowsInvalidTokenException() {
        String accessToken = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseAndValidateRefreshToken(accessToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expected refresh_token");
    }

    @Test
    @DisplayName("Malformed token string throws InvalidTokenException")
    void validateToken_Malformed_ThrowsInvalidTokenException() {
        assertThatThrownBy(() -> jwtService.parseAndValidateAccessToken("not-even-a-jwt"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Malformed");
    }
}
