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
    public ApiResponse<?> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        List<Problem> problems = problemService.visibleProblems(keyword, tags);
        List<Long> problemIds = problems.stream().map(Problem::getId).toList();
        Map<Long, String> statuses = problemService.solveStatuses(user, problemIds);
        Map<Long, ProblemService.SubmissionStats> stats = problemService.submissionStats(problemIds);
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        
        List<ProblemSummary> filtered = problems.stream()
                .map(problem -> ProblemSummary.from(
                        problem,
                        statuses.getOrDefault(problem.getId(), "UNATTEMPTED"),
                        stats.getOrDefault(problem.getId(), new ProblemService.SubmissionStats(0, 0))
                ))
                .filter(summary -> normalizedStatus.isBlank() || normalizedStatus.equals(summary.solveStatus()))
                .toList();

        if (page != null) {
            int total = filtered.size();
            int fromIndex = (page - 1) * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, total);
            List<ProblemSummary> pageList;
            if (fromIndex >= total || fromIndex < 0) {
                pageList = List.of();
            } else {
                pageList = filtered.subList(fromIndex, toIndex);
            }
            return ApiResponse.ok(new ProblemListResult(pageList, total));
        }

        return ApiResponse.ok(filtered);
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
        Map<Long, ProblemService.SubmissionStats> stats = problemService.submissionStats(List.of(id));
        ProblemService.SubmissionStats pStats = stats.getOrDefault(id, new ProblemService.SubmissionStats(0, 0));
        return ApiResponse.ok(ProblemDetail.from(problem, samples, testCaseFileStorage, solveStatus, pStats));
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
