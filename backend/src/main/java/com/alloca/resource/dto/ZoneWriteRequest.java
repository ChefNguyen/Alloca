package com.alloca.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OpenAPI {@code ZoneWriteRequest}, used by both create and update. */
public record ZoneWriteRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String floor,
        @NotBlank @Size(max = 100) String building) {
}
