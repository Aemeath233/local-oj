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
import com.coderushoj.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemSearchServiceTest {

    private ProblemMapper problemMapper;
    private ProblemTagMapper problemTagMapper;
    private ProblemTagRelationMapper problemTagRelationMapper;
    private ContestProblemVisibilityService contestProblemVisibilityService;
    private ProblemService problemService;
    private SubmissionMapper submissionMapper;
    private ProblemSearchService problemSearchService;

    @BeforeEach
    void setUp() {
        problemMapper = mock(ProblemMapper.class);
        problemTagMapper = mock(ProblemTagMapper.class);
        problemTagRelationMapper = mock(ProblemTagRelationMapper.class);
        contestProblemVisibilityService = mock(ContestProblemVisibilityService.class);
        problemService = mock(ProblemService.class);
        submissionMapper = mock(SubmissionMapper.class);

        problemSearchService = new ProblemSearchService(
                problemMapper, problemTagMapper, problemTagRelationMapper,
                contestProblemVisibilityService, problemService, submissionMapper
        );
    }

    @Test
    void solveStatusesReturnsCorrectStatuses() {
        CurrentUser user = new CurrentUser(1L, "test", Role.STUDENT, 0);
        List<Long> problemIds = List.of(1L, 2L, 3L);

        Submission sub1 = new Submission();
        sub1.setProblemId(1L);
        sub1.setVerdict(Verdict.AC);

        Submission sub2 = new Submission();
        sub2.setProblemId(2L);
        sub2.setVerdict(Verdict.WA);

        when(submissionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(sub1, sub2));

        Map<Long, String> statuses = problemSearchService.solveStatuses(user, problemIds);

        assertEquals(3, statuses.size());
        assertEquals("ACCEPTED", statuses.get(1L));
        assertEquals("ATTEMPTED", statuses.get(2L));
        assertEquals("UNATTEMPTED", statuses.get(3L));
    }

    @Test
    void visibleProblemsReturnsFilteredList() {
        Problem problem1 = new Problem();
        problem1.setId(1L);
        problem1.setTitle("A+B");

        when(problemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(problem1));

        List<Problem> problems = problemSearchService.visibleProblems("A+B");

        assertEquals(1, problems.size());
        assertEquals(1L, problems.get(0).getId());
    }

    @Test
    void visibleProblemsWithTagsReturnsEmptyIfTagsNotFound() {
        List<Problem> problems = problemSearchService.visibleProblems(null, List.of("unknown_tag"));
        assertTrue(problems.isEmpty());
    }
}
