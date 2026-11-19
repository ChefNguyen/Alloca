package com.alloca.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service password change (ALC-28). */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Size(min = 8, max = 100, message = "Current password must be between 8 and 100 characters")
        String currentPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
        String newPassword
) {
}
