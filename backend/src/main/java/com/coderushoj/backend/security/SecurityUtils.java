package com.coderushoj.backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static CurrentUser currentUser() {
        CurrentUser user = optionalCurrentUser();
        if (user == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        return user;
    }

    public static CurrentUser optionalCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            return null;
        }
        return user;
    }
}
