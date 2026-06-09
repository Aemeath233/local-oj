package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.security.SecurityUtils;
import com.coderushoj.backend.service.AuthService;
import com.coderushoj.backend.service.EmailVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request.username(), request.password()));
    }

    @PostMapping("/register-code")
    public ApiResponse<Object> sendRegisterCode(@Valid @RequestBody RegisterCodeRequest request) {
        emailVerificationService.sendRegisterCode(request.email());
        return ApiResponse.ok(null);
    }

    @PostMapping("/register")
    public ApiResponse<AuthService.LoginResult> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request.toCommand()));
    }

    @PostMapping("/reset-password-code")
    public ApiResponse<Object> sendResetPasswordCode(@Valid @RequestBody ResetPasswordCodeRequest request) {
        authService.sendResetPasswordCode(request.email());
        return ApiResponse.ok(null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Object> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.code(), request.newPassword());
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<Object> me() {
        return ApiResponse.ok(SecurityUtils.currentUser());
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RegisterCodeRequest(@NotBlank @Email String email) {
    }

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            String displayName,
            @NotBlank String password,
            @NotBlank String code
    ) {
        AuthService.RegisterCommand toCommand() {
            return new AuthService.RegisterCommand(username, email, displayName, password, code);
        }
    }

    public record ResetPasswordCodeRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank String code,
            @NotBlank String newPassword
    ) {
    }
}
