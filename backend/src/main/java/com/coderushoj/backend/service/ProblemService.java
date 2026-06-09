package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.common.enums.Verdict;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.ProblemSolutionMapper;
import com.coderushoj.common.mapper.ProblemTagMapper;
import com.coderushoj.common.mapper.ProblemTagRelationMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.TestCaseMapper;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.ProblemSolution;
import com.coderushoj.common.model.ProblemTag;
import com.coderushoj.common.model.ProblemTagRelation;
import com.coderushoj.common.model.Submission;
import com.coderushoj.common.model.TestCase;
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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProblemService.class);

    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final SubmissionMapper submissionMapper;
    private final TestCaseFileStorage testCaseFileStorage;
    private final ContestProblemVisibilityService contestProblemVisibilityService;
    private final ProblemSolutionMapper problemSolutionMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ProblemService(
            ProblemMapper problemMapper,
            TestCaseMapper testCaseMapper,
            SubmissionMapper submissionMapper,
            TestCaseFileStorage testCaseFileStorage,
            ContestProblemVisibilityService contestProblemVisibilityService,
            ProblemSolutionMapper problemSolutionMapper,
            ProblemTagMapper problemTagMapper,
            ProblemTagRelationMapper problemTagRelationMapper,
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate
    ) {
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.submissionMapper = submissionMapper;
        this.testCaseFileStorage = testCaseFileStorage;
        this.contestProblemVisibilityService = contestProblemVisibilityService;
        this.problemSolutionMapper = problemSolutionMapper;
        this.problemTagMapper = problemTagMapper;
        this.problemTagRelationMapper = problemTagRelationMapper;
        this.jdbcTemplate = jdbcTemplate;
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

    public Map<Long, SubmissionStats> submissionStats(List<Long> problemIds) {
        Map<Long, SubmissionStats> stats = new HashMap<>();
        for (Long id : problemIds) {
            stats.put(id, new SubmissionStats(0, 0));
        }
        if (problemIds.isEmpty()) {
            return stats;
        }

        // Query total submission count per problem
        List<Map<String, Object>> totalCounts = submissionMapper.selectMaps(new QueryWrapper<Submission>()
                .select("problem_id", "COUNT(*) as cnt")
                .in("problem_id", problemIds)
                .isNull("contest_id")
                .groupBy("problem_id"));
        for (Map<String, Object> map : totalCounts) {
            Long problemIdVal = getLongValue(map, "problem_id");
            Integer cntVal = getIntValue(map, "cnt");
            if (problemIdVal != null && cntVal != null) {
                stats.put(problemIdVal, new SubmissionStats(0, cntVal));
            }
        }

        // Query accepted (AC) submission count per problem
        List<Map<String, Object>> acCounts = submissionMapper.selectMaps(new QueryWrapper<Submission>()
                .select("problem_id", "COUNT(*) as cnt")
                .in("problem_id", problemIds)
                .eq("verdict", Verdict.AC.name())
                .isNull("contest_id")
                .groupBy("problem_id"));
        for (Map<String, Object> map : acCounts) {
            Long problemIdVal = getLongValue(map, "problem_id");
            Integer cntVal = getIntValue(map, "cnt");
            if (problemIdVal != null && cntVal != null) {
                SubmissionStats current = stats.get(problemIdVal);
                if (current != null) {
                    stats.put(problemIdVal, new SubmissionStats(cntVal, current.submitCount()));
                }
            }
        }

        return stats;
    }

    private Long getLongValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            val = map.get(key.toUpperCase());
        }
        return val instanceof Number ? ((Number) val).longValue() : null;
    }

    private Integer getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            val = map.get(key.toUpperCase());
        }
        return val instanceof Number ? ((Number) val).intValue() : null;
    }

    public com.coderushoj.backend.controller.ProblemController.ProblemListResult listProblemsPaged(
            String keyword,
            List<String> tags,
            CurrentUser user,
            String status,
            String sortBy,
            Integer page,
            int pageSize
    ) {
        contestProblemVisibilityService.releaseEndedContestLocks();

        StringBuilder sql = new StringBuilder();
        StringBuilder countSql = new StringBuilder();
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        sql.append("SELECT p.id, p.slug, p.title, p.difficulty, p.time_limit_ms, p.memory_limit_kb, p.visible, ");
        sql.append("COALESCE(stats.submit_cnt, 0) as submit_cnt, ");
        sql.append("COALESCE(stats.ac_cnt, 0) as ac_cnt, ");
        sql.append("(CAST(COALESCE(stats.ac_cnt, 0) AS DOUBLE) / CASE WHEN COALESCE(stats.submit_cnt, 0) > 0 THEN stats.submit_cnt ELSE 1 END) as ac_rate ");
        if (user != null) {
            sql.append(", s_user.user_status as user_status ");
        }
        sql.append("FROM problems p ");

        countSql.append("FROM problems p ");

        // Left join stats
        String statsJoin = "LEFT JOIN ( " +
                "    SELECT problem_id, COUNT(*) as submit_cnt, SUM(CASE WHEN verdict = 'AC' THEN 1 ELSE 0 END) as ac_cnt " +
                "    FROM submissions " +
                "    WHERE contest_id IS NULL " +
                "    GROUP BY problem_id " +
                ") stats ON p.id = stats.problem_id ";
        sql.append(statsJoin);
        countSql.append(statsJoin);

        // Left join user status if logged in
        if (user != null) {
            String userJoin = "LEFT JOIN ( " +
                    "    SELECT problem_id, MAX(CASE WHEN verdict = 'AC' THEN 2 ELSE 1 END) as user_status " +
                    "    FROM submissions " +
                    "    WHERE user_id = ? " +
                    "    GROUP BY problem_id " +
                    ") s_user ON p.id = s_user.problem_id ";
            sql.append(userJoin);
            countSql.append(userJoin);
            params.add(user.id());
            countParams.add(user.id());
        }

        // WHERE clauses
        StringBuilder where = new StringBuilder("WHERE p.visible = 1 ");

        // Tag filter
        if (tags != null && !tags.isEmpty()) {
            List<String> normalizedFilterTags = tags.stream()
                    .filter(java.util.Objects::nonNull)
                    .flatMap(value -> Arrays.stream(value.split("[,，]")))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
            if (!normalizedFilterTags.isEmpty()) {
                where.append("AND p.id IN ( ");
                where.append("    SELECT ptr.problem_id FROM problem_tag_relation ptr ");
                where.append("    JOIN problem_tags pt ON ptr.tag_id = pt.id ");
                where.append("    WHERE pt.name IN (");
                where.append(normalizedFilterTags.stream().map(t -> "?").collect(Collectors.joining(",")));
                where.append(") ");
                where.append("    GROUP BY ptr.problem_id HAVING COUNT(DISTINCT pt.id) = ");
                where.append(normalizedFilterTags.size());
                where.append(") ");

                for (String t : normalizedFilterTags) {
                    params.add(t);
                    countParams.add(t);
                }
            }
        }

        // Keyword filter
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (!normalizedKeyword.isEmpty()) {
            where.append("AND (p.title LIKE ? OR p.slug LIKE ? OR p.description LIKE ? ");
            params.add("%" + normalizedKeyword + "%");
            params.add("%" + normalizedKeyword + "%");
            params.add("%" + normalizedKeyword + "%");
            countParams.add("%" + normalizedKeyword + "%");
            countParams.add("%" + normalizedKeyword + "%");
            countParams.add("%" + normalizedKeyword + "%");

            Long id = parseId(normalizedKeyword);
            if (id != null) {
                where.append("OR p.id = ? ");
                params.add(id);
                countParams.add(id);
            }

            // Tags match via keyword
            where.append("OR p.id IN ( ");
            where.append("    SELECT ptr.problem_id FROM problem_tag_relation ptr ");
            where.append("    JOIN problem_tags pt ON ptr.tag_id = pt.id ");
            where.append("    WHERE pt.name LIKE ? ");
            where.append(") ) ");
            params.add("%" + normalizedKeyword + "%");
            countParams.add("%" + normalizedKeyword + "%");
        }

        // Status filter
        if (status != null && !status.trim().isEmpty()) {
            String normalizedStatus = status.trim().toUpperCase();
            if (user == null) {
                if (!"UNATTEMPTED".equals(normalizedStatus)) {
                    // Guest has no attempted/accepted problems
                    where.append("AND 1 = 0 ");
                }
            } else {
                if ("ACCEPTED".equals(normalizedStatus)) {
                    where.append("AND s_user.user_status = 2 ");
                } else if ("ATTEMPTED".equals(normalizedStatus)) {
                    where.append("AND s_user.user_status = 1 ");
                } else if ("UNATTEMPTED".equals(normalizedStatus)) {
                    where.append("AND (s_user.user_status IS NULL OR s_user.user_status = 0) ");
                }
            }
        }

        sql.append(where);
        countSql.insert(0, "SELECT COUNT(DISTINCT p.id) ");
        countSql.append(where);

        // Sorting
        String orderClause = "ORDER BY p.id ASC ";
        if ("ID_DESC".equalsIgnoreCase(sortBy)) {
            orderClause = "ORDER BY p.id DESC ";
        } else if ("DIFFICULTY_ASC".equalsIgnoreCase(sortBy)) {
            orderClause = "ORDER BY CASE WHEN UPPER(p.difficulty) IN ('EASY', '简单') THEN 1 WHEN UPPER(p.difficulty) IN ('MEDIUM', '中等') THEN 2 WHEN UPPER(p.difficulty) IN ('HARD', '困难') THEN 3 ELSE 0 END ASC, p.id ASC ";
        } else if ("DIFFICULTY_DESC".equalsIgnoreCase(sortBy)) {
            orderClause = "ORDER BY CASE WHEN UPPER(p.difficulty) IN ('EASY', '简单') THEN 1 WHEN UPPER(p.difficulty) IN ('MEDIUM', '中等') THEN 2 WHEN UPPER(p.difficulty) IN ('HARD', '困难') THEN 3 ELSE 0 END DESC, p.id DESC ";
        } else if ("AC_RATE_DESC".equalsIgnoreCase(sortBy)) {
            orderClause = "ORDER BY ac_rate DESC, p.id DESC ";
        } else if ("AC_RATE_ASC".equalsIgnoreCase(sortBy)) {
            orderClause = "ORDER BY ac_rate ASC, p.id ASC ";
        }
        sql.append(orderClause);

        // Count query
        long total = 0;
        try {
            Long countVal = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
            total = countVal != null ? countVal : 0L;
        } catch (Exception e) {
            log.error("Failed to query problems count", e);
        }

        // Limit & offset
        if (page != null && page > 0) {
            int offset = (page - 1) * pageSize;
            sql.append("LIMIT ? OFFSET ? ");
            params.add(pageSize);
            params.add(offset);
        }

        List<com.coderushoj.backend.controller.ProblemController.ProblemSummary> list = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            // Collect ids to populate tags
            List<Long> problemIds = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                problemIds.add(getLongValue(row, "id"));
            }

            // Query tags for these problems
            Map<Long, String> problemTagsMap = new HashMap<>();
            if (!problemIds.isEmpty()) {
                List<ProblemTagRelation> relations = problemTagRelationMapper.selectList(
                        new QueryWrapper<ProblemTagRelation>().in("problem_id", problemIds)
                );
                if (!relations.isEmpty()) {
                    List<Long> tagIds = relations.stream().map(ProblemTagRelation::getTagId).distinct().toList();
                    List<ProblemTag> matchingTags = problemTagMapper.selectList(
                            new QueryWrapper<ProblemTag>().in("id", tagIds)
                    );
                    Map<Long, String> tagIdToName = matchingTags.stream()
                            .collect(Collectors.toMap(ProblemTag::getId, ProblemTag::getName));
                    for (ProblemTagRelation rel : relations) {
                        String tagName = tagIdToName.get(rel.getTagId());
                        if (tagName != null) {
                            problemTagsMap.merge(rel.getProblemId(), tagName, (existing, add) -> existing + "," + add);
                        }
                    }
                }
            }

            for (Map<String, Object> row : rows) {
                Long id = getLongValue(row, "id");
                String slug = (String) row.getOrDefault("slug", row.get("SLUG"));
                String title = (String) row.getOrDefault("title", row.get("TITLE"));
                String difficulty = (String) row.getOrDefault("difficulty", row.get("DIFFICULTY"));
                Integer timeLimitMs = getIntValue(row, "time_limit_ms");
                Integer memoryLimitKb = getIntValue(row, "memory_limit_kb");
                Integer submitCnt = getIntValue(row, "submit_cnt");
                Integer acCnt = getIntValue(row, "ac_cnt");

                String solveStatusStr = "UNATTEMPTED";
                if (user != null) {
                    Integer userStatus = getIntValue(row, "user_status");
                    if (userStatus != null) {
                        if (userStatus == 2) {
                            solveStatusStr = "ACCEPTED";
                        } else if (userStatus == 1) {
                            solveStatusStr = "ATTEMPTED";
                        }
                    }
                }

                list.add(new com.coderushoj.backend.controller.ProblemController.ProblemSummary(
                        id,
                        slug,
                        title,
                        difficulty,
                        problemTagsMap.getOrDefault(id, ""),
                        timeLimitMs,
                        memoryLimitKb,
                        solveStatusStr,
                        acCnt != null ? acCnt : 0,
                        submitCnt != null ? submitCnt : 0
                ));
            }
        } catch (Exception e) {
            log.error("Failed to query problems paged list", e);
        }

        return new com.coderushoj.backend.controller.ProblemController.ProblemListResult(list, total);
    }

    public record SubmissionStats(int acceptedCount, int submitCount) {}
}
