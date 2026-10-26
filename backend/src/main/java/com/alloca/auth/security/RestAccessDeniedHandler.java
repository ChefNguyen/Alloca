package com.alloca.auth.security;
import com.alloca.common.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;


@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler{
    private final ObjectMapper objectMapper;
    
    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException accessDeniedException
    ) throws IOException {
        String correlationId = request.getHeader("X-Correlatiion-ID");
        if (correlationId == null || correlationId.isBlank()){
            correlationId  = UUID.randomUUID().toString();
        }

        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            HttpStatus.FORBIDDEN.value(),
            "ACCESS_DENIED",
            accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Access is denied",
            request.getRequestURI(),
            correlationId,
            null
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
