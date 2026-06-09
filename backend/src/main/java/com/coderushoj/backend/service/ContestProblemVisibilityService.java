package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.mapper.ContestMapper;
import com.coderushoj.common.mapper.ContestProblemVisibilityLockMapper;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.model.Contest;
import com.coderushoj.common.model.ContestProblemVisibilityLock;
import com.coderushoj.common.model.Problem;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContestProblemVisibilityService {
    private final ContestMapper contestMapper;
    private final ProblemMapper problemMapper;
    private final ContestProblemVisibilityLockMapper visibilityLockMapper;

    public ContestProblemVisibilityService(
            ContestMapper contestMapper,
            ProblemMapper problemMapper,
            ContestProblemVisibilityLockMapper visibilityLockMapper
    ) {
        this.contestMapper = contestMapper;
        this.problemMapper = problemMapper;
        this.visibilityLockMapper = visibilityLockMapper;
    }

    @Transactional
    public void hideForContest(Long contestId, List<Long> problemIds) {
        if (contestId == null || problemIds == null || problemIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || !contest.getEndTime().isAfter(now)) {
            releaseForContest(contestId);
            return;
        }
        for (Long problemId : new HashSet<>(problemIds)) {
            Problem problem = problemMapper.selectById(problemId);
            if (problem == null) {
                continue;
            }
            boolean alreadyLocked = visibilityLockMapper.selectCount(new QueryWrapper<ContestProblemVisibilityLock>()
                    .eq("problem_id", problemId)) > 0;
            if (!Boolean.TRUE.equals(problem.getVisible()) && !alreadyLocked) {
                continue;
            }
            if (visibilityLockMapper.selectOne(new QueryWrapper<ContestProblemVisibilityLock>()
                    .eq("contest_id", contestId)
                    .eq("problem_id", problemId)) == null) {
                visibilityLockMapper.insert(new ContestProblemVisibilityLock(contestId, problemId, now, problem.getVisible()));
            }
            if (Boolean.TRUE.equals(problem.getVisible())) {
                problem.setVisible(false);
                problemMapper.updateById(problem);
            }
        }
    }

    @Transactional
    public void releaseForContest(Long contestId) {
        if (contestId == null) {
            return;
        }
        List<ContestProblemVisibilityLock> locks = visibilityLockMapper.selectList(new QueryWrapper<ContestProblemVisibilityLock>()
                .eq("contest_id", contestId));
        visibilityLockMapper.delete(new QueryWrapper<ContestProblemVisibilityLock>().eq("contest_id", contestId));
        restoreUnlockedProblems(locks);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.contests.visibility-release-delay-ms:60000}")
    public void releaseEndedContestLocks() {
        List<ContestProblemVisibilityLock> locks = visibilityLockMapper.selectList(new QueryWrapper<>());
        if (locks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> endedContestIds = new HashSet<>();
        for (ContestProblemVisibilityLock lock : locks) {
            Contest contest = contestMapper.selectById(lock.getContestId());
            if (contest == null || !contest.getEndTime().isAfter(now)) {
                endedContestIds.add(lock.getContestId());
            }
        }
        for (Long contestId : endedContestIds) {
            releaseForContest(contestId);
        }
    }

    private void restoreUnlockedProblems(List<ContestProblemVisibilityLock> releasedLocks) {
        for (ContestProblemVisibilityLock lock : releasedLocks) {
            Long problemId = lock.getProblemId();
            if (visibilityLockMapper.selectCount(new QueryWrapper<ContestProblemVisibilityLock>().eq("problem_id", problemId)) > 0) {
                continue;
            }
            Problem problem = problemMapper.selectById(problemId);
            if (problem != null) {
                boolean originalVisible = Boolean.TRUE.equals(lock.getOriginalVisible());
                if (problem.getVisible() != originalVisible) {
                    problem.setVisible(originalVisible);
                    problemMapper.updateById(problem);
                }
            }
        }
    }
}
