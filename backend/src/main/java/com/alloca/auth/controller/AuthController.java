package com.alloca.auth.controller;

import com.alloca.auth.service.AuthService;

import lombok.RequiredArgsConstructor;

import com.alloca.auth.api.dto.LoginRequest;
import com.alloca.auth.api.dto.LoginResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;


/**
 * HTTP boundary for the Auth & RBAC module.
 *
 * <p>TODO (ALC-8, ALC-9): add login and refresh routes only after their request
 * DTOs, response DTOs and AuthService workflows are implemented from the OpenAPI contract.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // public AuthController(AuthService authService) {
    //     this.authService = authService;
    // }
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login( @Valid @RequestBody LoginRequest request){
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }



}
