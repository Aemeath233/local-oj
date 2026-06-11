package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.backend.security.SecurityUtils;
import com.coderushoj.backend.service.ContestService;
import com.coderushoj.backend.service.ProblemService;
import com.coderushoj.backend.service.SubmissionService;
import com.coderushoj.common.model.Contest;
import com.coderushoj.common.model.Problem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contests")
public class ContestController {
    private final ContestService contestService;
    private final ProblemService problemService;

    public ContestController(
            ContestService contestService,
            ProblemService problemService
    ) {
        this.contestService = contestService;
        this.problemService = problemService;
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
    public ResponseEntity<?> problemDetail(
            @PathVariable("id") Long id,
            @PathVariable("problemId") Long problemId,
            HttpServletRequest request
    ) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        Problem problem = contestService.getContestProblemDetail(id, problemId, user);

        String solveStatus = problemService.solveStatuses(user, List.of(problemId), id)
                .getOrDefault(problemId, "UNATTEMPTED");
        long updateEpoch = problem.getUpdatedAt() != null
                ? problem.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : 0;
        String etag = "\"cp" + id + "-" + problemId + "-" + updateEpoch + "-" + solveStatus.hashCode() + "\"";

        String ifNoneMatch = request.getHeader("If-None-Match");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache())
                    .build();
        }

        Map<Long, ProblemService.SubmissionStats> stats = problemService.submissionStats(List.of(problemId));
        ProblemService.SubmissionStats pStats = stats.getOrDefault(problemId, new ProblemService.SubmissionStats(0, 0));
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .body(ApiResponse.ok(ProblemController.ProblemDetail.from(problem, solveStatus, pStats)));
    }

    @GetMapping("/{id}/submissions")
    public ApiResponse<List<SubmissionService.SubmissionSummary>> submissions(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(contestService.listContestSubmissions(id, user));
    }

    @GetMapping("/{id}/leaderboard")
    public ApiResponse<List<ContestService.ContestStandingsRow>> leaderboard(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        contestService.requireContest(id, user);
        return ApiResponse.ok(contestService.calculateStandings(id, user));
    }

    @GetMapping("/{id}/leaderboard/export")
    public org.springframework.http.ResponseEntity<byte[]> exportLeaderboard(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        byte[] csvBytes = contestService.exportStandingsCsv(id, user);

        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contest-" + id + "-standings.csv\"")
                .body(csvBytes);
    }
}
