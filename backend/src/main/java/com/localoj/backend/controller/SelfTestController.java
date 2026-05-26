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

@RestController
@RequestMapping("/api/self-tests")
public class SelfTestController {
    private final SelfTestService selfTestService;

    public SelfTestController(SelfTestService selfTestService) {
        this.selfTestService = selfTestService;
    }

    @PostMapping
    public ApiResponse<SelfTestService.SelfTestResult> run(@Valid @RequestBody SelfTestRequest request) {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.ok(selfTestService.run(
                user,
                request.problemId(),
                request.contestId(),
                request.language(),
                request.sourceCode(),
                request.stdin()
        ));
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
