package com.alloca.common.api;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Business error that maps directly to an {@link ErrorResponse}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<ErrorResponse.FieldError> fieldErrors;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, null, null);
    }

    public ApiException(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<ErrorResponse.FieldError> fieldErrors,
            Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
        this.details = details;
    }

    public static ApiException notFound(ErrorCode code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Request validation failed",
                List.of(new ErrorResponse.FieldError(field, message)),
                null);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
