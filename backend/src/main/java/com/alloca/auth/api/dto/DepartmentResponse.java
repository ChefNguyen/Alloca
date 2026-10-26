package com.alloca.auth.api.dto;

import com.alloca.auth.domain.Department;
import java.time.Instant;
import java.util.UUID;

/** OpenAPI {@code Department}. */
public record DepartmentResponse(UUID id, String name, Instant createdAt) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.getId(), department.getName(), department.getCreatedAt());
    }
}
