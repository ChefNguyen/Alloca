package com.alloca.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cookie auth requires an explicit trusted Origin AND a non-simple header.
 * Other APIs authenticate only through the explicit Bearer header.
 */
public class CookieAuthCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout");
    private final List<String> allowedOrigins;
    private final RestAccessDeniedHandler deniedHandler;

    public CookieAuthCsrfFilter(List<String> allowedOrigins, RestAccessDeniedHandler deniedHandler) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.deniedHandler = deniedHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && PATHS.contains(path)) {
            String origin = request.getHeader("Origin");
            if (!"Alloca".equals(request.getHeader("X-Requested-With"))
                    || origin == null || !allowedOrigins.contains(origin)) {
                deniedHandler.handle(request, response,
                        new AccessDeniedException("Untrusted authentication request"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
