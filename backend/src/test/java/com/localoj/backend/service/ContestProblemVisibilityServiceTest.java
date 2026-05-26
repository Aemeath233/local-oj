package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.common.mapper.ContestMapper;
import com.localoj.common.mapper.ContestProblemVisibilityLockMapper;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.model.Contest;
import com.localoj.common.model.ContestProblemVisibilityLock;
import com.localoj.common.model.Problem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContestProblemVisibilityServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void endedContestDoesNotCreateVisibilityLocks() {
        ContestMapper contestMapper = mock(ContestMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        ContestProblemVisibilityLockMapper lockMapper = mock(ContestProblemVisibilityLockMapper.class);

        Contest contest = new Contest();
        contest.setId(1L);
        contest.setEndTime(LocalDateTime.now().minusMinutes(1));
        when(contestMapper.selectById(1L)).thenReturn(contest);
        when(lockMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        ContestProblemVisibilityService service = new ContestProblemVisibilityService(contestMapper, problemMapper, lockMapper);
        service.hideForContest(1L, List.of(100L));

        verify(lockMapper, never()).insert(any(ContestProblemVisibilityLock.class));
        verify(problemMapper, never()).updateById(any(Problem.class));
    }
}
