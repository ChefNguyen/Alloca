package com.alloca.auth.security;
import com.alloca.common.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "AUTH_TOKEN_INVALID",
            authException.getMessage() != null ? authException.getMessage() : "Full authentication is required to access this resource",
            request.getRequestURI(),
            correlationId,
            null
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}