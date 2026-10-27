package com.alloca.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest (
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    String email,

    @Size(min = 1, max = 100, message = "Full name must not be between 1 and 100")
    String fullName,

    Boolean isActive,

    @Size(min = 8, max = 100, message = "New password must be between 8 and 100 characters")
    String newPassword
){
    public boolean hasUpdates(){
        return email != null || fullName != null || isActive != null || newPassword != null;
    }
}
