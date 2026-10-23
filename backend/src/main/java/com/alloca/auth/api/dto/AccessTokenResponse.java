package com.alloca.auth.api.dto;

public record AccessTokenResponse (
    String accessToken,
    String tokenType,
    long expiresIn
){}
    
