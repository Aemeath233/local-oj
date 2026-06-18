package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.common.enums.Verdict;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.ProblemTagMapper;
import com.coderushoj.common.mapper.ProblemTagRelationMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.ProblemTag;
import com.coderushoj.common.model.ProblemTagRelation;
import com.coderushoj.common.model.Submission;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProblemSearchService {

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ContestProblemVisibilityService contestProblemVisibilityService;
    private final ProblemService problemService;
    private final SubmissionMapper submissionMapper;

    public ProblemSearchService(
            ProblemMapper problemMapper,
            ProblemTagMapper problemTagMapper,
            ProblemTagRelationMapper problemTagRelationMapper,
            ContestProblemVisibilityService contestProblemVisibilityService,
            ProblemService problemService,
            SubmissionMapper submissionMapper
    ) {
        this.problemMapper = problemMapper;
        this.problemTagMapper = problemTagMapper;
        this.problemTagRelationMapper = problemTagRelationMapper;
        this.contestProblemVisibilityService = contestProblemVisibilityService;
        this.problemService = problemService;
        this.submissionMapper = submissionMapper;
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
        problemService.populateTags(problems);
        return problems;
    }

    private Long parseId(String keyword) {
        try {
            return Long.parseLong(keyword);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Map<Long, String> solveStatuses(CurrentUser user, List<Long> problemIds) {
        return solveStatuses(user, problemIds, null);
    }

    public Map<Long, String> solveStatuses(CurrentUser user, List<Long> problemIds, Long contestId) {
        Map<Long, String> statuses = new HashMap<>();
        for (Long problemId : problemIds) {
            statuses.put(problemId, "UNATTEMPTED");
        }
        if (user == null || problemIds.isEmpty()) {
            return statuses;
        }
        QueryWrapper<Submission> query = new QueryWrapper<Submission>()
                .select("problem_id", "verdict")
                .eq("user_id", user.id())
                .in("problem_id", problemIds);
        if (contestId != null) {
            query.eq("contest_id", contestId);
        }
        List<Submission> submissions = submissionMapper.selectList(query);
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
}
