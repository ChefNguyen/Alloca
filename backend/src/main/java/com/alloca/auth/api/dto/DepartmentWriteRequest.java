package com.alloca.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OpenAPI {@code DepartmentWriteRequest}, used by both create and update. */
public record DepartmentWriteRequest(@NotBlank @Size(max = 100) String name) {
}
