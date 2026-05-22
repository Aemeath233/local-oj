package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.ContestService;
import com.localoj.backend.service.ProblemService;
import com.localoj.backend.service.TestCaseFileStorage;
import com.localoj.common.model.Contest;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
import com.localoj.common.model.TestCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/contests")
public class ContestController {
    private final ContestService contestService;
    private final ProblemService problemService;
    private final TestCaseFileStorage testCaseFileStorage;

    public ContestController(
            ContestService contestService,
            ProblemService problemService,
            TestCaseFileStorage testCaseFileStorage
    ) {
        this.contestService = contestService;
        this.problemService = problemService;
        this.testCaseFileStorage = testCaseFileStorage;
    }

    @GetMapping
    public ApiResponse<List<Contest>> list() {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(contestService.listContests(user));
    }

    @GetMapping("/{id}")
    public ApiResponse<Contest> detail(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(contestService.requireContest(id, user));
    }

    @GetMapping("/{id}/registration")
    public ApiResponse<ContestService.ContestRegistrationStatus> registration(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(contestService.registrationStatus(id, user));
    }

    @PostMapping("/{id}/register")
    public ApiResponse<ContestService.ContestRegistrationStatus> register(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.ok(contestService.registerContest(id, user));
    }

    @GetMapping("/{id}/problems")
    public ApiResponse<List<ContestService.ContestProblemDetail>> problems(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(contestService.getContestProblems(id, user));
    }

    @GetMapping("/{id}/problems/{problemId}")
    public ApiResponse<ProblemController.ProblemDetail> problemDetail(
            @PathVariable("id") Long id,
            @PathVariable("problemId") Long problemId
    ) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        Problem problem = contestService.getContestProblemDetail(id, problemId, user);
        List<TestCase> samples = problemService.testCases(problemId).stream()
                .filter(testCase -> Boolean.TRUE.equals(testCase.getSample()))
                .toList();
        String solveStatus = problemService.solveStatuses(user, List.of(problemId))
                .getOrDefault(problemId, "UNATTEMPTED");
        return ApiResponse.ok(ProblemController.ProblemDetail.from(problem, samples, testCaseFileStorage, solveStatus));
    }

    @GetMapping("/{id}/submissions")
    public ApiResponse<List<Submission>> submissions(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.ok(contestService.listContestSubmissions(id, user));
    }

    @GetMapping("/{id}/leaderboard")
    public ApiResponse<List<ContestService.ContestStandingsRow>> leaderboard(@PathVariable("id") Long id) {
        return ApiResponse.ok(contestService.calculateStandings(id));
    }
}
