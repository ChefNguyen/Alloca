package com.alloca.auth.dto;

public record AccessTokenResponse (
    String accessToken,
    String tokenType,
    long expiresIn
){}
    
