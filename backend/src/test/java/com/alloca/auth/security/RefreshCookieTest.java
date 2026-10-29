package com.alloca.auth.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RefreshCookieTest {
    @Test
    void secureHostOnlyCookieHasBoundedLifetimeAndMatchingDeletionScope() {
        RefreshCookie cookie = new RefreshCookie(true, "Lax");
        assertThat(cookie.issue("secret", 604800)).contains("HttpOnly", "Secure", "SameSite=Lax",
                "Path=/api/v1/auth", "Max-Age=604800").doesNotContain("Domain=");
        assertThat(cookie.clear()).contains("alloca_refresh=;", "Max-Age=0", "Path=/api/v1/auth",
                "Secure", "HttpOnly", "SameSite=Lax");
    }

    @Test
    void crossSiteRequiresSecureAndInvalidSameSiteFailsFast() {
        assertThatThrownBy(() -> new RefreshCookie(false, "None")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshCookie(true, "anything")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RefreshCookie(true, "None").issue("token", 60)).contains("SameSite=None", "Secure");
    }

    @Test
    void localHttpOverrideKeepsHttpOnlyAndSameSite() {
        assertThat(new RefreshCookie(false, "Lax").issue("token", 60))
                .contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
    }
}
