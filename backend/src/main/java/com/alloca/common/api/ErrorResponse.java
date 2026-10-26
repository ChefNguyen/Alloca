package com.alloca.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ErrorResponse (
    Instant timestamp,
    int status,
    String code,
    String message,
    String path,
    String correlationId,
    List<FieldError> fieldErrors,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> details
){
    public ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors
    ){
        this(timestamp, status, code, message, path, correlationId, fieldErrors, null);
    }

    public record FieldError(
        String field,
        String message
    ){}
}
