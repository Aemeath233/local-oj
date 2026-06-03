package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.ProblemService;
import com.localoj.backend.service.TestCaseFileStorage;
import com.localoj.common.enums.Role;
import com.localoj.common.model.Problem;
import com.localoj.common.model.TestCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {
    private final ProblemService problemService;
    private final TestCaseFileStorage testCaseFileStorage;

    public ProblemController(ProblemService problemService, TestCaseFileStorage testCaseFileStorage) {
        this.problemService = problemService;
        this.testCaseFileStorage = testCaseFileStorage;
    }

    @GetMapping
    public ApiResponse<?> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "sortBy", defaultValue = "ID_ASC") String sortBy
    ) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        int cappedPageSize = Math.min(100, Math.max(1, pageSize));
        ProblemListResult result = problemService.listProblemsPaged(keyword, tags, user, status, sortBy, page, cappedPageSize);
        if (page == null) {
            return ApiResponse.ok(result.list());
        }
        return ApiResponse.ok(result);
    }

    private int difficultyValue(String difficulty) {
        if (difficulty == null) return 0;
        return switch (difficulty.toUpperCase()) {
            case "EASY", "简单" -> 1;
            case "MEDIUM", "中等" -> 2;
            case "HARD", "困难" -> 3;
            default -> 0;
        };
    }

    private double acRate(ProblemSummary summary) {
        if (summary.submitCount() == null || summary.submitCount() == 0) {
            return 0.0;
        }
        return (double) summary.acceptedCount() / summary.submitCount();
    }

    @GetMapping("/daily")
    public ApiResponse<ProblemSummary> daily() {
        List<Problem> problems = problemService.visibleProblems();
        if (problems.isEmpty()) {
            return ApiResponse.ok(null);
        }
        long epochDay = java.time.LocalDate.now().toEpochDay();
        int index = (int) (epochDay % problems.size());
        Problem dailyProblem = problems.get(index);

        CurrentUser user = SecurityUtils.optionalCurrentUser();
        String solveStatus = problemService.solveStatuses(user, List.of(dailyProblem.getId()))
                .getOrDefault(dailyProblem.getId(), "UNATTEMPTED");
        Map<Long, ProblemService.SubmissionStats> stats = problemService.submissionStats(List.of(dailyProblem.getId()));
        ProblemService.SubmissionStats dailyStats = stats.getOrDefault(dailyProblem.getId(), new ProblemService.SubmissionStats(0, 0));
        return ApiResponse.ok(ProblemSummary.from(dailyProblem, solveStatus, dailyStats));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        Problem problem = problemService.requireProblem(id);
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        if (!Boolean.TRUE.equals(problem.getVisible())) {
            if (user == null || (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Problem not found");
            }
        }

        String solveStatus = problemService.solveStatuses(user, List.of(id))
                .getOrDefault(id, "UNATTEMPTED");
        long updateEpoch = problem.getUpdatedAt() != null
                ? problem.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0;
        String etag = "\"p" + id + "-" + updateEpoch + "-" + solveStatus.hashCode() + "\"";

        String ifNoneMatch = request.getHeader("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }

        List<TestCase> samples = problemService.testCases(id).stream()
                .filter(testCase -> Boolean.TRUE.equals(testCase.getSample()))
                .toList();
        Map<Long, ProblemService.SubmissionStats> stats = problemService.submissionStats(List.of(id));
        ProblemService.SubmissionStats pStats = stats.getOrDefault(id, new ProblemService.SubmissionStats(0, 0));
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .body(ApiResponse.ok(ProblemDetail.from(problem, samples, testCaseFileStorage, solveStatus, pStats)));
    }

    public record ProblemSummary(
            Long id,
            String slug,
            String title,
            String difficulty,
            String tags,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            String solveStatus,
            Integer acceptedCount,
            Integer submitCount
    ) {
        static ProblemSummary from(Problem problem, String solveStatus, ProblemService.SubmissionStats stats) {
            return new ProblemSummary(
                    problem.getId(),
                    problem.getSlug(),
                    problem.getTitle(),
                    problem.getDifficulty(),
                    problem.getTags(),
                    problem.getTimeLimitMs(),
                    problem.getMemoryLimitKb(),
                    solveStatus,
                    stats.acceptedCount(),
                    stats.submitCount()
            );
        }
    }

    public record ProblemDetail(
            Long id,
            String slug,
            String title,
            String description,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            String difficulty,
            String tags,
            List<SampleCase> samples,
            String solveStatus,
            Integer acceptedCount,
            Integer submitCount
    ) {
        static ProblemDetail from(Problem problem, List<TestCase> samples, TestCaseFileStorage testCaseFileStorage, String solveStatus, ProblemService.SubmissionStats stats) {
            return new ProblemDetail(
                    problem.getId(),
                    problem.getSlug(),
                    problem.getTitle(),
                    problem.getDescription(),
                    problem.getTimeLimitMs(),
                    problem.getMemoryLimitKb(),
                    problem.getDifficulty(),
                    problem.getTags(),
                    samples.stream().map(testCase -> SampleCase.from(testCase, testCaseFileStorage)).toList(),
                    solveStatus,
                    stats.acceptedCount(),
                    stats.submitCount()
            );
        }
    }

    public record SampleCase(String inputText, String expectedOutput) {
        static SampleCase from(TestCase testCase, TestCaseFileStorage testCaseFileStorage) {
            return new SampleCase(testCaseFileStorage.readInput(testCase), testCaseFileStorage.readExpectedOutput(testCase));
        }
    }

    public record ProblemListResult(List<ProblemSummary> list, long total) {}
}
