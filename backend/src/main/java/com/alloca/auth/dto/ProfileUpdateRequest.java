package com.alloca.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Self-service profile update (ALC-28). Unknown fields (e.g. role, departmentId, isActive) are rejected. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ProfileUpdateRequest(
        @Size(min = 1, max = 100, message = "Full name must be between 1 and 100 characters")
        String fullName,
        @Email(message = "Email must be valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email
) {
}
