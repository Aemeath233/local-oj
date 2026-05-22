package com.localoj.common.model;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("contest_problem_visibility_locks")
public class ContestProblemVisibilityLock {
    private Long contestId;
    private Long problemId;
    private LocalDateTime createdAt;

    public ContestProblemVisibilityLock() {
    }

    public ContestProblemVisibilityLock(Long contestId, Long problemId, LocalDateTime createdAt) {
        this.contestId = contestId;
        this.problemId = problemId;
        this.createdAt = createdAt;
    }

    public Long getContestId() {
        return contestId;
    }

    public void setContestId(Long contestId) {
        this.contestId = contestId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
