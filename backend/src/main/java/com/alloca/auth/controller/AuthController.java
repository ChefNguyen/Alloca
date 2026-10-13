package com.alloca.auth.controller;

import com.alloca.auth.service.AuthService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for the Auth & RBAC module.
 *
 * <p>TODO (ALC-8, ALC-9): add login and refresh routes only after their request
 * DTOs, response DTOs and AuthService workflows are implemented from the OpenAPI contract.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
}
