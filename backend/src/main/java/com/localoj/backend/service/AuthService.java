package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.security.JwtService;
import com.localoj.common.enums.Role;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailVerificationService emailVerificationService
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
    }

    public LoginResult login(String identifier, String password) {
        User user = resolveUser(identifier);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名/邮箱或密码不正确");
        }
        return new LoginResult(jwtService.issue(user), UserView.from(user));
    }

    private User resolveUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        String trimmed = identifier.trim();
        if (trimmed.contains("@")) {
            User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", trimmed));
            if (user != null) {
                return user;
            }
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", trimmed));
        if (user != null) {
            return user;
        }
        // Fallback: if identifier looked like a username but wasn't found, also try email
        if (!trimmed.contains("@")) {
            return userMapper.selectOne(new QueryWrapper<User>().eq("email", trimmed));
        }
        return null;
    }

    @Transactional
    public LoginResult register(RegisterCommand command) {
        String username = normalizeUsername(command.username());
        String email = emailVerificationService.normalizeEmail(command.email());
        if (command.password() == null || command.password().length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        if (userMapper.selectCount(new QueryWrapper<User>().eq("username", username)) > 0) {
            throw new IllegalArgumentException("用户名已被注册");
        }
        if (userMapper.selectCount(new QueryWrapper<User>().eq("email", email)) > 0) {
            throw new IllegalArgumentException("邮箱已被注册");
        }

        emailVerificationService.consumeRegisterCode(email, command.code());

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setDisplayName(defaultDisplayName(command.displayName(), username));
        user.setRole(Role.STUDENT);
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        return new LoginResult(jwtService.issue(user), UserView.from(user));
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String normalized = username.trim();
        if (!normalized.matches("[A-Za-z0-9_]{3,32}")) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线，长度 3-32 位");
        }
        return normalized;
    }

    private String defaultDisplayName(String displayName, String username) {
        return displayName == null || displayName.isBlank() ? username : displayName.trim();
    }

    public record RegisterCommand(String username, String email, String displayName, String password, String code) {
    }

    public record LoginResult(String token, UserView user) {
    }

    public record UserView(
            Long id,
            String username,
            String email,
            String displayName,
            String avatarUrl,
            String studentNo,
            String major,
            String role
    ) {
        static UserView from(User user) {
            return new UserView(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.getStudentNo(),
                    user.getMajor(),
                    user.getRole().name()
            );
        }
    }
}
