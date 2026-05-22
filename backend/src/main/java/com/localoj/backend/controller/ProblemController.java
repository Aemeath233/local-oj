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
    public ApiResponse<List<ProblemSummary>> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status
    ) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        List<Problem> problems = problemService.visibleProblems(keyword);
        Map<Long, String> statuses = problemService.solveStatuses(user, problems.stream().map(Problem::getId).toList());
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        return ApiResponse.ok(problems.stream()
                .map(problem -> ProblemSummary.from(problem, statuses.getOrDefault(problem.getId(), "UNATTEMPTED")))
                .filter(summary -> normalizedStatus.isBlank() || normalizedStatus.equals(summary.solveStatus()))
                .toList());
    }

    @GetMapping("/{id}")
    public ApiResponse<ProblemDetail> detail(@PathVariable("id") Long id) {
        Problem problem = problemService.requireProblem(id);
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        if (!Boolean.TRUE.equals(problem.getVisible())) {
            if (user == null || (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN)) {
                throw new IllegalArgumentException("Problem not found");
            }
        }
        List<TestCase> samples = problemService.testCases(id).stream()
                .filter(testCase -> Boolean.TRUE.equals(testCase.getSample()))
                .toList();
        String solveStatus = problemService.solveStatuses(user, List.of(id))
                .getOrDefault(id, "UNATTEMPTED");
        return ApiResponse.ok(ProblemDetail.from(problem, samples, testCaseFileStorage, solveStatus));
    }

    public record ProblemSummary(
            Long id,
            String slug,
            String title,
            String difficulty,
            String tags,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            String solveStatus
    ) {
        static ProblemSummary from(Problem problem, String solveStatus) {
            return new ProblemSummary(
                    problem.getId(),
                    problem.getSlug(),
                    problem.getTitle(),
                    problem.getDifficulty(),
                    problem.getTags(),
                    problem.getTimeLimitMs(),
                    problem.getMemoryLimitKb(),
                    solveStatus
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
            String solveStatus
    ) {
        static ProblemDetail from(Problem problem, List<TestCase> samples, TestCaseFileStorage testCaseFileStorage, String solveStatus) {
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
                    solveStatus
            );
        }
    }

    public record SampleCase(String inputText, String expectedOutput) {
        static SampleCase from(TestCase testCase, TestCaseFileStorage testCaseFileStorage) {
            return new SampleCase(testCaseFileStorage.readInput(testCase), testCaseFileStorage.readExpectedOutput(testCase));
        }
    }
}
