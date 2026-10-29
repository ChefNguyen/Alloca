package com.alloca.auth.security;

import org.springframework.http.ResponseCookie;

/** Host-only refresh cookie. JavaScript never receives its value. */
public class RefreshCookie {
    public static final String NAME = "alloca_refresh";
    public static final String PATH = "/api/v1/auth";
    private final boolean secure;
    private final String sameSite;

    public RefreshCookie(boolean secure, String sameSite) {
        if (!java.util.Set.of("Lax", "Strict", "None").contains(sameSite)) {
            throw new IllegalArgumentException("AUTH_COOKIE_SAME_SITE must be Lax, Strict or None");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires Secure cookies");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public String issue(String token, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, token).httpOnly(true).secure(secure)
                .sameSite(sameSite).path(PATH).maxAge(maxAgeSeconds).build().toString();
    }

    public String clear() {
        return issue("", 0);
    }
}
