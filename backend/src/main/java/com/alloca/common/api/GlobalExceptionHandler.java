package com.alloca.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.alloca.auth.exception.InvalidTokenException;
import com.alloca.auth.exception.ExpiredTokenException;
import org.springframework.web.bind.annotation.ExceptionHandler; 
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final MediaType PROBLEM_JSON = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
        BadCredentialsException ex,
        HttpServletRequest request
    ){
        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "AUTH_INVALID_CREDENTIALS",
            ex.getMessage(),
            request.getRequestURI(),
            resolveCorrelationId(request),
            null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .contentType(PROBLEM_JSON)
                            .body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ){
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                                    .map(err -> new ErrorResponse.FieldError(err.getField(), err.getDefaultMessage()))
                                    .toList();
        
        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            "Validation failed",
            request.getRequestURI(),
            resolveCorrelationId(request),
            fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .contentType(PROBLEM_JSON)
                            .body(error);
    }

    private String resolveCorrelationId(HttpServletRequest request){
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null || correlationId.isBlank()){
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
        InvalidTokenException ex,
        HttpServletRequest request
    ){
        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "AUTH_TOKEN_INVALID", // Mã chuẩn theo OpenAPI
            ex.getMessage(),
            request.getRequestURI(),
            resolveCorrelationId(request),
            null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                             .contentType(PROBLEM_JSON)
                             .body(error);
    }

    @ExceptionHandler(ExpiredTokenException.class)
    public ResponseEntity<ErrorResponse> handleExpiredToken(
        ExpiredTokenException ex,
        HttpServletRequest request
    ) {
        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            "AUTH_TOKEN_EXPIRED", // Mã chuẩn theo OpenAPI
            ex.getMessage(),
            request.getRequestURI(),
            resolveCorrelationId(request),
            null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                             .contentType(PROBLEM_JSON)
                             .body(error);
    }
}
