package com.coderushoj.backend.security;

import com.coderushoj.common.enums.Role;

public record CurrentUser(Long id, String username, Role role, Integer tokenVersion) {
    public CurrentUser(Long id, String username, Role role) {
        this(id, username, role, null);
    }
}
