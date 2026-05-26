package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.ProblemSolutionMapper;
import com.localoj.common.mapper.ProblemTagMapper;
import com.localoj.common.mapper.ProblemTagRelationMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.TestCaseMapper;
import com.localoj.common.model.Problem;
import com.localoj.common.model.ProblemSolution;
import com.localoj.common.model.ProblemTag;
import com.localoj.common.model.ProblemTagRelation;
import com.localoj.common.model.Submission;
import com.localoj.common.model.TestCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProblemService {
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final SubmissionMapper submissionMapper;
    private final TestCaseFileStorage testCaseFileStorage;
    private final ContestProblemVisibilityService contestProblemVisibilityService;
    private final ProblemSolutionMapper problemSolutionMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;

    public ProblemService(
            ProblemMapper problemMapper,
            TestCaseMapper testCaseMapper,
            SubmissionMapper submissionMapper,
            TestCaseFileStorage testCaseFileStorage,
            ContestProblemVisibilityService contestProblemVisibilityService,
            ProblemSolutionMapper problemSolutionMapper,
            ProblemTagMapper problemTagMapper,
            ProblemTagRelationMapper problemTagRelationMapper
    ) {
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.submissionMapper = submissionMapper;
        this.testCaseFileStorage = testCaseFileStorage;
        this.contestProblemVisibilityService = contestProblemVisibilityService;
        this.problemSolutionMapper = problemSolutionMapper;
        this.problemTagMapper = problemTagMapper;
        this.problemTagRelationMapper = problemTagRelationMapper;
    }

    public List<Problem> visibleProblems() {
        return visibleProblems(null, null);
    }

    public List<Problem> visibleProblems(String keyword) {
        return visibleProblems(keyword, null);
    }

    public List<Problem> visibleProblems(String keyword, List<String> filterTags) {
        contestProblemVisibilityService.releaseEndedContestLocks();
        QueryWrapper<Problem> query = new QueryWrapper<Problem>()
                .eq("visible", true);

        if (filterTags != null && !filterTags.isEmpty()) {
            List<String> normalizedFilterTags = filterTags.stream()
                    .filter(value -> value != null)
                    .flatMap(value -> Arrays.stream(value.split("[,，]")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
            if (!normalizedFilterTags.isEmpty()) {
                List<ProblemTag> matchingTags = problemTagMapper.selectList(
                        new QueryWrapper<ProblemTag>().in("name", normalizedFilterTags)
                );
                if (matchingTags.size() < normalizedFilterTags.size()) {
                    return List.of();
                }
                List<Long> tagIds = matchingTags.stream().map(ProblemTag::getId).toList();
                String sql = "SELECT problem_id FROM problem_tag_relation WHERE tag_id IN (" +
                        tagIds.stream().map(String::valueOf).collect(Collectors.joining(",")) +
                        ") GROUP BY problem_id HAVING COUNT(DISTINCT tag_id) = " + tagIds.size();
                query.inSql("id", sql);
            }
        }

        String normalized = keyword == null ? "" : keyword.trim();
        if (!normalized.isBlank()) {
            Long id = parseId(normalized);

            // Search tags relational mapping
            List<ProblemTag> matchingTags = problemTagMapper.selectList(
                    new QueryWrapper<ProblemTag>().like("name", normalized)
            );
            List<Long> problemIdsFromTags = List.of();
            if (!matchingTags.isEmpty()) {
                List<Long> tagIds = matchingTags.stream().map(ProblemTag::getId).toList();
                problemIdsFromTags = problemTagRelationMapper.selectList(
                        new QueryWrapper<ProblemTagRelation>().in("tag_id", tagIds)
                ).stream().map(ProblemTagRelation::getProblemId).distinct().toList();
            }

            final List<Long> finalProblemIdsFromTags = problemIdsFromTags;

            query.and(wrapper -> {
                wrapper.like("title", normalized)
                        .or().like("slug", normalized)
                        .or().like("description", normalized);
                if (!finalProblemIdsFromTags.isEmpty()) {
                    wrapper.or().in("id", finalProblemIdsFromTags);
                }
                if (id != null) {
                    wrapper.or().eq("id", id);
                }
            });
        }
        query.orderByDesc("id");
        List<Problem> problems = problemMapper.selectList(query);
        populateTags(problems);
        return problems;
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
        populateTags(List.of(problem));
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
        saveProblemTags(problem.getId(), command.tags());
        insertTestCases(problem.getId(), command.testCases(), now);
        populateTags(List.of(problem));
        return problem;
    }

    @Transactional
    public Problem updateProblem(Long id, CreateProblemCommand command) {
        LocalDateTime now = LocalDateTime.now();
        Problem problem = requireProblem(id);
        fillProblem(problem, command);
        problem.setUpdatedAt(now);
        problemMapper.updateById(problem);
        saveProblemTags(id, command.tags());
        testCaseMapper.delete(new QueryWrapper<TestCase>().eq("problem_id", id));
        insertTestCases(id, command.testCases(), now);
        populateTags(List.of(problem));
        return problem;
    }

    @Transactional
    public void deleteProblem(Long id) {
        submissionMapper.delete(new QueryWrapper<Submission>().eq("problem_id", id));
        problemSolutionMapper.delete(new QueryWrapper<ProblemSolution>().eq("problem_id", id));
        problemMapper.deleteById(id);
        testCaseFileStorage.deleteProblemDirectory(id);
    }

    public void populateTags(List<Problem> problems) {
        if (problems == null || problems.isEmpty()) {
            return;
        }
        List<Long> problemIds = problems.stream().map(Problem::getId).toList();
        List<ProblemTagRelation> relations = problemTagRelationMapper.selectList(
                new QueryWrapper<ProblemTagRelation>().in("problem_id", problemIds)
        );
        if (relations.isEmpty()) {
            for (Problem p : problems) {
                p.setTags("");
            }
            return;
        }
        List<Long> tagIds = relations.stream().map(ProblemTagRelation::getTagId).distinct().toList();
        List<ProblemTag> tags = problemTagMapper.selectList(
                new QueryWrapper<ProblemTag>().in("id", tagIds)
        );
        Map<Long, String> tagIdToName = tags.stream()
                .collect(Collectors.toMap(ProblemTag::getId, ProblemTag::getName));

        Map<Long, List<String>> problemIdToTagNames = new HashMap<>();
        for (ProblemTagRelation rel : relations) {
            String name = tagIdToName.get(rel.getTagId());
            if (name != null) {
                problemIdToTagNames.computeIfAbsent(rel.getProblemId(), k -> new ArrayList<>()).add(name);
            }
        }
        for (Problem p : problems) {
            List<String> names = problemIdToTagNames.get(p.getId());
            if (names == null || names.isEmpty()) {
                p.setTags("");
            } else {
                p.setTags(String.join(",", names));
            }
        }
    }

    private void saveProblemTags(Long problemId, String tagsString) {
        problemTagRelationMapper.delete(
                new QueryWrapper<ProblemTagRelation>().eq("problem_id", problemId)
        );
        if (tagsString == null || tagsString.isBlank()) {
            return;
        }
        List<String> names = Arrays.stream(tagsString.split("[,，]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();

        for (String name : names) {
            ProblemTag tag = problemTagMapper.selectOne(
                    new QueryWrapper<ProblemTag>().eq("name", name)
            );
            if (tag == null) {
                tag = new ProblemTag();
                tag.setName(name);
                tag.setColor("#909399");
                tag.setCreatedAt(LocalDateTime.now());
                tag.setUpdatedAt(LocalDateTime.now());
                problemTagMapper.insert(tag);
            }
            problemTagRelationMapper.insert(new ProblemTagRelation(problemId, tag.getId()));
        }
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
