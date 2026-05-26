package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ContestMapper;
import com.localoj.common.mapper.ContestProblemMapper;
import com.localoj.common.mapper.ContestRegistrationMapper;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.Contest;
import com.localoj.common.model.ContestProblem;
import com.localoj.common.model.ContestRegistration;
import com.localoj.common.model.Submission;
import com.localoj.common.model.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void standingsIgnoreSubmissionsOutsideContestWindow() {
        ContestMapper contestMapper = mock(ContestMapper.class);
        ContestProblemMapper contestProblemMapper = mock(ContestProblemMapper.class);
        ContestRegistrationMapper contestRegistrationMapper = mock(ContestRegistrationMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        ContestProblemVisibilityService visibilityService = mock(ContestProblemVisibilityService.class);

        LocalDateTime start = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 22, 12, 0);
        Contest contest = contest(1L, start, end, "ACM");
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(contestRegistrationMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                new ContestRegistration(1L, 1L, start.minusMinutes(30)),
                new ContestRegistration(1L, 2L, start.minusMinutes(20))
        ));
        when(contestProblemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new ContestProblem(1L, 100L, 0)));
        when(submissionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                submission(1L, 1L, 100L, Verdict.AC, start.minusMinutes(5)),
                submission(2L, 1L, 100L, Verdict.WA, start.plusMinutes(10)),
                submission(3L, 1L, 100L, Verdict.AC, start.plusMinutes(40)),
                submission(4L, 2L, 100L, Verdict.WA, start.plusMinutes(5)),
                submission(5L, 2L, 100L, Verdict.AC, end.plusMinutes(1))
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(1L, "alice"), user(2L, "bob")));

        ContestService service = new ContestService(
                contestMapper,
                contestProblemMapper,
                contestRegistrationMapper,
                problemMapper,
                submissionMapper,
                userMapper,
                visibilityService
        );

        List<ContestService.ContestStandingsRow> rows = service.calculateStandings(1L);

        assertEquals(2, rows.size());
        assertEquals(1L, rows.get(0).userId());
        assertEquals(1, rows.get(0).acceptedCount());
        assertEquals(60, rows.get(0).totalPenaltyMinutes());
        assertTrue(rows.get(0).problemDetails().get(100L).accepted());
        assertEquals(1, rows.get(0).problemDetails().get(100L).failedAttempts());

        assertEquals(2L, rows.get(1).userId());
        assertEquals(0, rows.get(1).acceptedCount());
        assertEquals(1, rows.get(1).problemDetails().get(100L).failedAttempts());
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportStandingsCsvGeneratesValidCsv() {
        ContestMapper contestMapper = mock(ContestMapper.class);
        ContestProblemMapper contestProblemMapper = mock(ContestProblemMapper.class);
        ContestRegistrationMapper contestRegistrationMapper = mock(ContestRegistrationMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        ContestProblemVisibilityService visibilityService = mock(ContestProblemVisibilityService.class);

        LocalDateTime start = LocalDateTime.of(2026, 5, 22, 10, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 22, 12, 0);
        Contest contest = contest(1L, start, end, "ACM");
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(contestRegistrationMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                new ContestRegistration(1L, 1L, start.minusMinutes(30))
        ));
        when(contestProblemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new ContestProblem(1L, 100L, 0)));
        when(submissionMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(
                submission(1L, 1L, 100L, Verdict.AC, start.plusMinutes(10))
        ));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(1L, "alice")));

        com.localoj.common.model.Problem problem = new com.localoj.common.model.Problem();
        problem.setId(100L);
        problem.setTitle("A+B");
        when(problemMapper.selectBatchIds(any())).thenReturn(List.of(problem));

        ContestService service = new ContestService(
                contestMapper,
                contestProblemMapper,
                contestRegistrationMapper,
                problemMapper,
                submissionMapper,
                userMapper,
                visibilityService
        );

        byte[] csvBytes = service.exportStandingsCsv(1L, null);
        String csv = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("排名,用户名,昵称,通过数,总罚时,A (A+B)"));
        assertTrue(csv.contains("1,alice,alice,1,10,+ (10)"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hidingContestReleasesProblemVisibilityLocks() {
        ContestMapper contestMapper = mock(ContestMapper.class);
        ContestProblemMapper contestProblemMapper = mock(ContestProblemMapper.class);
        ContestRegistrationMapper contestRegistrationMapper = mock(ContestRegistrationMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        ContestProblemVisibilityService visibilityService = mock(ContestProblemVisibilityService.class);

        LocalDateTime start = LocalDateTime.of(2026, 5, 22, 10, 0);
        Contest contest = contest(1L, start, start.plusHours(2), "ACM");
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(contestProblemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(new ContestProblem(1L, 100L, 0)));

        ContestService service = new ContestService(
                contestMapper,
                contestProblemMapper,
                contestRegistrationMapper,
                problemMapper,
                submissionMapper,
                userMapper,
                visibilityService
        );

        service.setContestVisibility(1L, false);

        verify(visibilityService).releaseForContest(1L);
        verify(visibilityService, never()).hideForContest(any(), any());
    }

    private Contest contest(Long id, LocalDateTime start, LocalDateTime end, String type) {
        Contest contest = new Contest();
        contest.setId(id);
        contest.setTitle("Contest");
        contest.setStartTime(start);
        contest.setEndTime(end);
        contest.setVisible(Boolean.TRUE);
        contest.setType(type);
        contest.setCreatedAt(start.minusDays(1));
        contest.setUpdatedAt(start.minusDays(1));
        return contest;
    }

    private Submission submission(Long id, Long userId, Long problemId, Verdict verdict, LocalDateTime createdAt) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setUserId(userId);
        submission.setProblemId(problemId);
        submission.setContestId(1L);
        submission.setVerdict(verdict);
        submission.setCreatedAt(createdAt);
        return submission;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setDisplayName(username);
        return user;
    }
}
