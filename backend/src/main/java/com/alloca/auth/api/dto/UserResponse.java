package com.alloca.auth.api.dto;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;

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
    public static UserResponse from(User user){
        DepartmentSummaryResponse dept = user.getDepartment() != null ? new DepartmentSummaryResponse(user.getDepartment().getId(),
                                            user.getDepartment().getName()) : null;
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getFullname(),
            user.getEmail(),
            user.getRole(),
            user.isActive(),
            dept,
            user.getCreatedAt()
        );
    }
}
