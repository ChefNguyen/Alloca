package com.alloca.auth.security;

import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.exception.InvalidTokenException;
import com.alloca.auth.repository.UserRepository;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ALC-25: JwtAuthenticationFilter Unit Tests")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtAuthenticationEntryPoint authenticationEntryPoint;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private User activeUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userId = UUID.randomUUID();
        Department department = new Department(UUID.randomUUID(), "Engineering");
        activeUser = new User(userId, "testuser", "test@alloca.com", "hash", "Test User", Role.EMPLOYEE, department);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("No Authorization header -> bypass filter and continue chain without authentication")
    void doFilter_NoAuthHeader_PassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, userRepository);
    }

    @Test
    @DisplayName("Non-Bearer header -> bypass filter and continue chain")
    void doFilter_NonBearerHeader_PassesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService, userRepository);
    }

    @Test
    @DisplayName("Valid token & active user -> populates SecurityContext and continues chain")
    void doFilter_ValidTokenAndActiveUser_PopulatesSecurityContext() throws Exception {
        String token = "valid-access-token-123";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("token_type", "access_token")
                .claim("role", "EMPLOYEE")
                .build();

        when(jwtService.parseAndValidateAccessToken(token)).thenReturn(claims);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UserPrincipal.class);
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getUsername()).isEqualTo("testuser");
        assertThat(principal.getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    @DisplayName("Invalid token -> calls AuthenticationEntryPoint and clears SecurityContext")
    void doFilter_InvalidToken_CallsEntryPoint() throws Exception {
        String token = "tampered-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.parseAndValidateAccessToken(token))
                .thenThrow(new InvalidTokenException("Invalid token signature"));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("BR-AUTH-01: Deactivated user -> rejected via AuthenticationEntryPoint")
    void doFilter_DeactivatedUser_CallsEntryPoint() throws Exception {
        String token = "valid-token-deactivated-user";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .build();

        activeUser.deactivate();

        when(jwtService.parseAndValidateAccessToken(token)).thenReturn(claims);
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser));

        filter.doFilterInternal(request, response, filterChain);

        verify(authenticationEntryPoint).commence(eq(request), eq(response), any());
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
