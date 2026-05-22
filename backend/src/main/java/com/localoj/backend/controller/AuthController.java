package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.AuthService;
import com.localoj.backend.service.EmailVerificationService;
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
}
