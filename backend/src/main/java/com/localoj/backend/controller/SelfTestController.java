package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.SelfTestService;
import com.localoj.common.enums.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/self-tests")
public class SelfTestController {
    private final SelfTestService selfTestService;
    private final ExecutorService selfTestExecutor = Executors.newFixedThreadPool(16, r -> {
        Thread thread = new Thread(r);
        thread.setName("self-test-pool-" + thread.getId());
        thread.setDaemon(true);
        return thread;
    });

    public SelfTestController(SelfTestService selfTestService) {
        this.selfTestService = selfTestService;
    }

    @PostMapping
    public CompletableFuture<ApiResponse<SelfTestService.SelfTestResult>> run(@Valid @RequestBody SelfTestRequest request) {
        CurrentUser user = SecurityUtils.currentUser();
        return CompletableFuture.supplyAsync(() -> ApiResponse.ok(selfTestService.run(
                user,
                request.problemId(),
                request.contestId(),
                request.language(),
                request.sourceCode(),
                request.stdin()
        )), selfTestExecutor);
    }

    public record SelfTestRequest(
            @NotNull Long problemId,
            Long contestId,
            @NotNull Language language,
            @NotBlank @Size(max = 100_000) String sourceCode,
            @Size(max = 100_000) String stdin
    ) {
    }
}
