package com.coderushoj.common.model;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("problem_tag_relation")
public class ProblemTagRelation {
    private Long problemId;
    private Long tagId;

    public ProblemTagRelation() {}

    public ProblemTagRelation(Long problemId, Long tagId) {
        this.problemId = problemId;
        this.tagId = tagId;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }
}
