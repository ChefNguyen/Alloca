package com.alloca.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    @Size(min = 20, message = "Refresh token must be at least 20 characters")
    String refreshToken
){}
