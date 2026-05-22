package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.ProblemSolutionMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.TestCaseMapper;
import com.localoj.common.model.Problem;
import com.localoj.common.model.ProblemSolution;
import com.localoj.common.model.Submission;
import com.localoj.common.model.TestCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProblemService {
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final SubmissionMapper submissionMapper;
    private final TestCaseFileStorage testCaseFileStorage;
    private final ContestProblemVisibilityService contestProblemVisibilityService;
    private final ProblemSolutionMapper problemSolutionMapper;

    public ProblemService(
            ProblemMapper problemMapper,
            TestCaseMapper testCaseMapper,
            SubmissionMapper submissionMapper,
            TestCaseFileStorage testCaseFileStorage,
            ContestProblemVisibilityService contestProblemVisibilityService,
            ProblemSolutionMapper problemSolutionMapper
    ) {
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.submissionMapper = submissionMapper;
        this.testCaseFileStorage = testCaseFileStorage;
        this.contestProblemVisibilityService = contestProblemVisibilityService;
        this.problemSolutionMapper = problemSolutionMapper;
    }

    public List<Problem> visibleProblems() {
        return visibleProblems(null);
    }

    public List<Problem> visibleProblems(String keyword) {
        contestProblemVisibilityService.releaseEndedContestLocks();
        QueryWrapper<Problem> query = new QueryWrapper<Problem>()
                .eq("visible", true);
        String normalized = keyword == null ? "" : keyword.trim();
        if (!normalized.isBlank()) {
            Long id = parseId(normalized);
            query.and(wrapper -> {
                wrapper.like("title", normalized)
                        .or().like("slug", normalized)
                        .or().like("description", normalized)
                        .or().like("tags", normalized);
                if (id != null) {
                    wrapper.or().eq("id", id);
                }
            });
        }
        query.orderByDesc("id");
        return problemMapper.selectList(query);
    }

    public Map<Long, String> solveStatuses(CurrentUser user, List<Long> problemIds) {
        Map<Long, String> statuses = new HashMap<>();
        for (Long problemId : problemIds) {
            statuses.put(problemId, "UNATTEMPTED");
        }
        if (user == null || problemIds.isEmpty()) {
            return statuses;
        }
        List<Submission> submissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .select("problem_id", "verdict")
                .eq("user_id", user.id())
                .in("problem_id", problemIds));
        for (Submission submission : submissions) {
            String current = statuses.getOrDefault(submission.getProblemId(), "UNATTEMPTED");
            if ("ACCEPTED".equals(current)) {
                continue;
            }
            if (submission.getVerdict() == Verdict.AC) {
                statuses.put(submission.getProblemId(), "ACCEPTED");
            } else {
                statuses.put(submission.getProblemId(), "ATTEMPTED");
            }
        }
        return statuses;
    }

    private Long parseId(String keyword) {
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Problem requireProblem(Long id) {
        Problem problem = problemMapper.selectById(id);
        if (problem == null) {
            throw new IllegalArgumentException("Problem not found");
        }
        return problem;
    }

    public List<TestCase> testCases(Long problemId) {
        return testCaseMapper.selectList(new QueryWrapper<TestCase>()
                .eq("problem_id", problemId)
                .orderByAsc("sort_order")
                .orderByAsc("id"));
    }

    @Transactional
    public Problem createProblem(CreateProblemCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Problem problem = new Problem();
        fillProblem(problem, command);
        problem.setCreatedAt(now);
        problem.setUpdatedAt(now);
        problemMapper.insert(problem);
        insertTestCases(problem.getId(), command.testCases(), now);
        return problem;
    }

    @Transactional
    public Problem updateProblem(Long id, CreateProblemCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Problem problem = requireProblem(id);
        fillProblem(problem, command);
        problem.setUpdatedAt(now);
        problemMapper.updateById(problem);
        testCaseMapper.delete(new QueryWrapper<TestCase>().eq("problem_id", id));
        insertTestCases(id, command.testCases(), now);
        return problem;
    }

    @Transactional
    public void deleteProblem(Long id) {
        submissionMapper.delete(new QueryWrapper<Submission>().eq("problem_id", id));
        problemSolutionMapper.delete(new QueryWrapper<ProblemSolution>().eq("problem_id", id));
        problemMapper.deleteById(id);
        testCaseFileStorage.deleteProblemDirectory(id);
    }

    private void fillProblem(Problem problem, CreateProblemCommand command) {
        problem.setSlug(command.slug());
        problem.setTitle(command.title());
        problem.setDescription(command.description());
        problem.setTimeLimitMs(command.timeLimitMs());
        problem.setMemoryLimitKb(command.memoryLimitKb());
        problem.setDifficulty(command.difficulty());
        problem.setTags(command.tags());
        problem.setVisible(command.visible());
    }

    private void insertTestCases(Long problemId, List<TestCaseCommand> testCaseCommands, LocalDateTime now) {
        List<TestCase> testCases = testCaseFileStorage.materializeCases(problemId, testCaseCommands, now);
        for (TestCase testCase : testCases) {
            testCaseMapper.insert(testCase);
        }
    }

    public record CreateProblemCommand(
            String slug,
            String title,
            String description,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            String difficulty,
            String tags,
            Boolean visible,
            List<TestCaseCommand> testCases
    ) {
    }

    public record TestCaseCommand(
            String uploadToken,
            String name,
            String inputFile,
            String outputFile,
            Long inputSize,
            Long outputSize,
            Integer score,
            Boolean sample
    ) {
    }
}
