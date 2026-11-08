package com.alloca.auth.dto;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import java.util.UUID;

/**
 * OpenAPI {@code UserSummary} representation.
 */
public record UserSummaryResponse(
        UUID id,
        String username,
        String fullName,
        Role role
) {
    public static UserSummaryResponse from(User user) {
        if (user == null) {
            return null;
        }
        return new UserSummaryResponse(
                user.getId(),
                user.getUsername(),
                user.getFullname(),
                user.getRole()
        );
    }
}

