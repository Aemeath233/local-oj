package com.coderushoj.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        String secret = "thisisaverysecuresecretkeythisshouldbelongenoughforhmacsha256";
        jwtService = new JwtService(objectMapper, secret, 60, 10080);
    }

    @Test
    void issueAndParseAccessToken() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.STUDENT);
        user.setAuthTokenVersion(2);

        String token = jwtService.issue(user);
        Optional<CurrentUser> parsed = jwtService.parse(token);

        assertTrue(parsed.isPresent());
        CurrentUser currentUser = parsed.get();
        assertEquals(1L, currentUser.id());
        assertEquals("testuser", currentUser.username());
        assertEquals(2, currentUser.tokenVersion());

        // An access token should not be parsed as a refresh token
        Optional<CurrentUser> asRefresh = jwtService.parseRefreshToken(token);
        assertFalse(asRefresh.isPresent());
    }

    @Test
    void issueAndParseRefreshToken() {
        User user = new User();
        user.setId(2L);
        user.setUsername("admin");
        user.setRole(Role.ADMIN);
        user.setAuthTokenVersion(0);

        String refreshToken = jwtService.issueRefreshToken(user);
        Optional<CurrentUser> parsed = jwtService.parseRefreshToken(refreshToken);

        assertTrue(parsed.isPresent());
        CurrentUser currentUser = parsed.get();
        assertEquals(2L, currentUser.id());
        assertEquals("admin", currentUser.username());

        // A refresh token should not be parsed as an access token
        Optional<CurrentUser> asAccess = jwtService.parse(refreshToken);
        assertFalse(asAccess.isPresent());
    }

    @Test
    void parseFailsWithInvalidSignature() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setRole(Role.STUDENT);
        user.setAuthTokenVersion(2);

        String token = jwtService.issue(user);
        String invalidToken = token.substring(0, token.length() - 5) + "aaaaa";

        Optional<CurrentUser> parsed = jwtService.parse(invalidToken);
        assertFalse(parsed.isPresent());
    }
}
