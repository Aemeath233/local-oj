package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.SubmissionService;
import com.localoj.common.enums.Language;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
import com.localoj.common.model.SubmissionCaseResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {
    private final SubmissionService submissionService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public SubmissionController(
            SubmissionService submissionService,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.submissionService = submissionService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sse-ticket")
    public ApiResponse<java.util.Map<String, String>> generateSseTicket() {
        CurrentUser user = SecurityUtils.currentUser();
        String ticket = java.util.UUID.randomUUID().toString();
        try {
            String userJson = objectMapper.writeValueAsString(user);
            redisTemplate.opsForValue().set("sse:ticket:" + ticket, userJson, 10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ticket", e);
        }
        return ApiResponse.ok(java.util.Map.of("ticket", ticket));
    }

    @PostMapping
    public ApiResponse<Submission> submit(@Valid @RequestBody SubmitRequest request) {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.ok(submissionService.submit(user, request.problemId(), request.language(), request.sourceCode(), request.contestId()));
    }

    @GetMapping
    public ApiResponse<List<SubmissionService.SubmissionSummary>> list() {
        return ApiResponse.ok(submissionService.listSummaries(SecurityUtils.currentUser()));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubmissionDetail> detail(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.currentUser();
        Submission submission = submissionService.requireVisibleSubmission(user, id);
        Problem problem = submissionService.problemForSubmission(submission);
        List<SubmissionCaseResult> cases = submissionService.caseResults(user, id);
        return ApiResponse.ok(new SubmissionDetail(submission, problem, cases));
    }

    public record SubmitRequest(
            @NotNull Long problemId,
            @NotNull Language language,
            @NotBlank @Size(max = 100_000) String sourceCode,
            Long contestId
    ) {
    }

    public record SubmissionDetail(Submission submission, Problem problem, List<SubmissionCaseResult> cases) {
    }
}
