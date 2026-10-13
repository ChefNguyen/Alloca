package com.alloca.auth.controller;

import com.alloca.auth.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for account administration and the current-user profile.
 *
 * <p>TODO (ALC-26 to ALC-28): implement only the documented account lifecycle,
 * role/department assignment and profile/password flows.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
}
