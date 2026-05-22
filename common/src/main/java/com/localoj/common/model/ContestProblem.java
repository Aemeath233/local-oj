package com.localoj.common.model;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("contest_problems")
public class ContestProblem {
    private Long contestId;
    private Long problemId;
    private Integer sortOrder;

    public ContestProblem() {
    }

    public ContestProblem(Long contestId, Long problemId, Integer sortOrder) {
        this.contestId = contestId;
        this.problemId = problemId;
        this.sortOrder = sortOrder;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
