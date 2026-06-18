package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.backend.security.JwtService;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private EmailVerificationService emailVerificationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        emailVerificationService = mock(EmailVerificationService.class);

        authService = new AuthService(userMapper, passwordEncoder, jwtService, emailVerificationService);
    }

    @Test
    void loginSuccess() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setRole(Role.STUDENT);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("password", "hash")).thenReturn(true);
        when(jwtService.issue(user)).thenReturn("access_token");
        when(jwtService.issueRefreshToken(user)).thenReturn("refresh_token");

        AuthService.LoginResult result = authService.login("test", "password");

        assertEquals("access_token", result.token());
        assertEquals("refresh_token", result.refreshToken());
        assertEquals("test", result.user().username());
    }

    @Test
    void loginFailsWithWrongPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPasswordHash("hash");
        user.setEnabled(true);

        when(userMapper.selectOne(any(QueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login("test", "wrong"));
    }

    @Test
    void refreshSuccess() {
        CurrentUser currentUser = new CurrentUser(1L, "test", Role.STUDENT, 0);
        when(jwtService.parseRefreshToken("valid_refresh_token")).thenReturn(Optional.of(currentUser));

        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setEnabled(true);
        user.setAuthTokenVersion(0);
        user.setRole(Role.STUDENT);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(jwtService.issue(user)).thenReturn("new_access_token");
        when(jwtService.issueRefreshToken(user)).thenReturn("new_refresh_token");

        AuthService.LoginResult result = authService.refresh("valid_refresh_token");

        assertEquals("new_access_token", result.token());
        assertEquals("new_refresh_token", result.refreshToken());
    }

    @Test
    void refreshFailsIfTokenRevoked() {
        CurrentUser currentUser = new CurrentUser(1L, "test", Role.STUDENT, 0);
        when(jwtService.parseRefreshToken("valid_refresh_token")).thenReturn(Optional.of(currentUser));

        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setEnabled(true);
        user.setAuthTokenVersion(1); // Mismatch! Revoked

        when(userMapper.selectById(1L)).thenReturn(user);

        assertThrows(IllegalArgumentException.class, () -> authService.refresh("valid_refresh_token"));
    }
}
