package com.localoj.backend.security;

import com.localoj.common.enums.Role;

public record CurrentUser(Long id, String username, Role role) {
}
