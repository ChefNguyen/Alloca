package com.alloca.auth.api.dto;

import java.util.UUID;

import com.alloca.auth.domain.Role;

import jakarta.validation.constraints.NotNull;

public record UserAccessUpdateRequest (
    @NotNull(message = "Role is required")
    Role role,
    UUID departmentId   
){
}
