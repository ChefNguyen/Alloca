package com.alloca.auth.api.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    String refreshToken,
    long refreshExpiresIn,
    UserResponse user
) {
    
}
