package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.SandboxSettingsService;
import com.localoj.backend.service.SmtpSettingsService;
import com.localoj.backend.service.SystemSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {
    private final SmtpSettingsService smtpSettingsService;
    private final SandboxSettingsService sandboxSettingsService;
    private final SystemSettingsService systemSettingsService;

    public AdminSettingsController(
            SmtpSettingsService smtpSettingsService,
            SandboxSettingsService sandboxSettingsService,
            SystemSettingsService systemSettingsService
    ) {
        this.smtpSettingsService = smtpSettingsService;
        this.sandboxSettingsService = sandboxSettingsService;
        this.systemSettingsService = systemSettingsService;
    }

    @GetMapping("/system")
    public ApiResponse<SystemSettingsService.SystemSettingsView> system() {
        return ApiResponse.ok(systemSettingsService.view());
    }

    @PutMapping("/system")
    public ApiResponse<SystemSettingsService.SystemSettingsView> updateSystem(@Valid @RequestBody SystemSettingsRequest request) {
        return ApiResponse.ok(systemSettingsService.update(request.toCommand()));
    }

    @GetMapping("/smtp")
    public ApiResponse<SmtpSettingsService.SmtpSettingsView> smtp() {
        return ApiResponse.ok(smtpSettingsService.view());
    }

    @PutMapping("/smtp")
    public ApiResponse<SmtpSettingsService.SmtpSettingsView> updateSmtp(@Valid @RequestBody SmtpSettingsRequest request) {
        return ApiResponse.ok(smtpSettingsService.update(request.toCommand()));
    }

    @GetMapping("/sandbox")
    public ApiResponse<SandboxSettingsService.SandboxSettingsView> sandbox() {
        return ApiResponse.ok(sandboxSettingsService.view());
    }

    @PutMapping("/sandbox")
    public ApiResponse<SandboxSettingsService.SandboxSettingsView> updateSandbox(@Valid @RequestBody SandboxSettingsRequest request) {
        return ApiResponse.ok(sandboxSettingsService.update(request.toCommand()));
    }

    public record SmtpSettingsRequest(
            Boolean enabled,
            String host,
            @Min(1) Integer port,
            String username,
            String password,
            String fromAddress,
            String fromName,
            Boolean authEnabled,
            Boolean useSsl,
            Boolean useStarttls
    ) {
        SmtpSettingsService.UpdateSmtpSettingsCommand toCommand() {
            return new SmtpSettingsService.UpdateSmtpSettingsCommand(
                    enabled,
                    host,
                    port,
                    username,
                    password,
                    fromAddress,
                    fromName,
                    authEnabled,
                    useSsl,
                    useStarttls
            );
        }
    }

    public record SandboxSettingsRequest(
            @Min(1) Integer workerThreads,
            @Min(1) Integer maxConcurrentRuns,
            @Min(1000) Integer compileTimeoutMs,
            @Min(64) Integer defaultOutputLimitKb,
            @Min(1) Integer maxProcessCount,
            @Min(1) Integer caseConcurrentRuns
    ) {
        SandboxSettingsService.UpdateSandboxSettingsCommand toCommand() {
            return new SandboxSettingsService.UpdateSandboxSettingsCommand(
                    workerThreads,
                    maxConcurrentRuns,
                    compileTimeoutMs,
                    defaultOutputLimitKb,
                    maxProcessCount,
                    caseConcurrentRuns
            );
        }
    }

    public record SystemSettingsRequest(String allowedOrigins) {
        SystemSettingsService.UpdateSystemSettingsCommand toCommand() {
            return new SystemSettingsService.UpdateSystemSettingsCommand(allowedOrigins);
        }
    }
}
