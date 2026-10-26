package com.alloca.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.alloca.auth.domain.Role;
import com.alloca.auth.security.JwtAuthenticationFilter;
import com.alloca.auth.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Helpers for {@code @WebMvcTest} slices that import the real {@code SecurityConfig}.
 *
 * <p>The slice mocks {@link JwtAuthenticationFilter}; {@link #passThrough} makes the mock delegate
 * to the next filter (a bare Mockito mock would swallow every request), and {@link #admin()} /
 * {@link #employee()} inject an authenticated principal the way the real filter would.
 */
public final class WebSecurityTestSupport {

    private WebSecurityTestSupport() {
    }

    public static void passThrough(JwtAuthenticationFilter filter) throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(filter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    public static RequestPostProcessor admin() {
        return as(Role.ADMIN);
    }

    public static RequestPostProcessor manager() {
        return as(Role.MANAGER);
    }

    public static RequestPostProcessor employee() {
        return as(Role.EMPLOYEE);
    }

    private static RequestPostProcessor as(Role role) {
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), role.name().toLowerCase(), role, null);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
