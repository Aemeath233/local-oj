package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.enums.Verdict;
import com.coderushoj.common.mapper.ContestMapper;
import com.coderushoj.common.mapper.ContestProblemMapper;
import com.coderushoj.common.mapper.ContestRegistrationMapper;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Contest;
import com.coderushoj.common.model.ContestProblem;
import com.coderushoj.common.model.ContestRegistration;
import com.coderushoj.common.model.Submission;
import com.coderushoj.common.model.User;
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
