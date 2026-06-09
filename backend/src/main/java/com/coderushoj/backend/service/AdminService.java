package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.enums.SubmissionStatus;
import com.coderushoj.common.enums.Verdict;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.TestCaseMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.Submission;
import com.coderushoj.common.model.User;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminService {
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemService problemService;

    public AdminService(
            ProblemMapper problemMapper,
            TestCaseMapper testCaseMapper,
            SubmissionMapper submissionMapper,
            UserMapper userMapper,
            ProblemService problemService
    ) {
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemService = problemService;
    }

    public Dashboard dashboard() {
        long submissionCount = countSubmissions(new QueryWrapper<>());
        long acceptedCount = countSubmissions(new QueryWrapper<Submission>().eq("verdict", Verdict.AC.name()));
        long pendingCount = countSubmissions(new QueryWrapper<Submission>().eq("status", SubmissionStatus.PENDING.name()));
        long runningCount = countSubmissions(new QueryWrapper<Submission>().eq("status", SubmissionStatus.RUNNING.name()));
        Map<String, Long> verdictCounts = new LinkedHashMap<>();
        for (Verdict verdict : Verdict.values()) {
            verdictCounts.put(verdict.name(), countSubmissions(new QueryWrapper<Submission>().eq("verdict", verdict.name())));
        }
        return new Dashboard(
                countProblems(new QueryWrapper<>()),
                countProblems(new QueryWrapper<Problem>().eq("visible", true)),
                countUsers(new QueryWrapper<>()),
                submissionCount,
                acceptedCount,
                pendingCount,
                runningCount,
                verdictCounts,
                recentSubmissions(10)
        );
    }

    public List<ProblemSummary> problems() {
        List<Problem> problems = problemMapper.selectList(new QueryWrapper<Problem>().orderByDesc("id"));
        problemService.populateTags(problems);
        return problems.stream()
                .map(this::toProblemSummary)
                .toList();
    }

    public ProblemSummary updateProblemVisibility(Long problemId, Boolean visible) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new IllegalArgumentException("Problem not found");
        }
        problem.setVisible(Boolean.TRUE.equals(visible));
        problemMapper.updateById(problem);
        Problem updated = problemMapper.selectById(problemId);
        problemService.populateTags(List.of(updated));
        return toProblemSummary(updated);
    }

    public List<SubmissionSummary> recentSubmissions(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return submissionMapper.selectList(new QueryWrapper<Submission>()
                        .orderByDesc("id")
                        .last("LIMIT " + boundedLimit))
                .stream()
                .map(this::toSubmissionSummary)
                .toList();
    }

    private ProblemSummary toProblemSummary(Problem problem) {
        return new ProblemSummary(
                problem.getId(),
                problem.getSlug(),
                problem.getTitle(),
                problem.getDifficulty(),
                problem.getTags(),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitKb(),
                Boolean.TRUE.equals(problem.getVisible()),
                countTestCases(problem.getId()),
                countSubmissions(new QueryWrapper<Submission>().eq("problem_id", problem.getId()))
        );
    }

    private SubmissionSummary toSubmissionSummary(Submission submission) {
        User user = userMapper.selectById(submission.getUserId());
        Problem problem = problemMapper.selectById(submission.getProblemId());
        return new SubmissionSummary(
                submission.getId(),
                submission.getUserId(),
                user == null ? null : user.getUsername(),
                user == null ? null : user.getDisplayName(),
                submission.getProblemId(),
                problem == null ? null : problem.getTitle(),
                submission.getLanguage().name(),
                submission.getStatus().name(),
                submission.getVerdict() == null ? null : submission.getVerdict().name(),
                submission.getScore(),
                submission.getTimeMs(),
                submission.getMemoryKb(),
                submission.getCreatedAt(),
                submission.getJudgedAt()
        );
    }

    private long countProblems(QueryWrapper<Problem> query) {
        return problemMapper.selectCount(query);
    }

    private long countUsers(QueryWrapper<User> query) {
        return userMapper.selectCount(query);
    }

    private long countSubmissions(QueryWrapper<Submission> query) {
        return submissionMapper.selectCount(query);
    }

    private long countTestCases(Long problemId) {
        return testCaseMapper.selectCount(new QueryWrapper<com.coderushoj.common.model.TestCase>()
                .eq("problem_id", problemId));
    }

    public record Dashboard(
            long problemCount,
            long visibleProblemCount,
            long userCount,
            long submissionCount,
            long acceptedSubmissionCount,
            long pendingSubmissionCount,
            long runningSubmissionCount,
            Map<String, Long> verdictCounts,
            List<SubmissionSummary> recentSubmissions
    ) {
    }

    public record ProblemSummary(
            Long id,
            String slug,
            String title,
            String difficulty,
            String tags,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            Boolean visible,
            Long testCaseCount,
            Long submissionCount
    ) {
    }

    public record SubmissionSummary(
            Long id,
            Long userId,
            String username,
            String displayName,
            Long problemId,
            String problemTitle,
            String language,
            String status,
            String verdict,
            Integer score,
            Long timeMs,
            Long memoryKb,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime judgedAt
    ) {
    }
}
