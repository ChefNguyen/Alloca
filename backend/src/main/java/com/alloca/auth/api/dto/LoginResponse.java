package com.alloca.auth.api.dto;

public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    @com.fasterxml.jackson.annotation.JsonIgnore String refreshToken,
    long refreshExpiresIn,
    UserResponse user
) {
    
}
