package com.alloca.auth.api.dto;

import com.alloca.auth.domain.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse (
    UUID id,
    String username,
    String fullname,
    String email,
    Role role,
    boolean isActive,
    DepartmentSummaryResponse department,
    Instant createdAt
){
}
