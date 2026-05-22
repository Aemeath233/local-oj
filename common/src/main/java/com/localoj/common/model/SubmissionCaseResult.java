package com.localoj.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.localoj.common.enums.Verdict;

import java.time.LocalDateTime;

@TableName("submission_case_results")
public class SubmissionCaseResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long testCaseId;
    private Integer caseIndex;
    private Verdict verdict;
    private Long timeMs;
    private Long memoryKb;
    private String stdoutText;
    private String stderrText;
    private String message;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(Long testCaseId) {
        this.testCaseId = testCaseId;
    }

    public Integer getCaseIndex() {
        return caseIndex;
    }

    public void setCaseIndex(Integer caseIndex) {
        this.caseIndex = caseIndex;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
    }

    public Long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(Long timeMs) {
        this.timeMs = timeMs;
    }

    public Long getMemoryKb() {
        return memoryKb;
    }

    public void setMemoryKb(Long memoryKb) {
        this.memoryKb = memoryKb;
    }

    public String getStdoutText() {
        return stdoutText;
    }

    public void setStdoutText(String stdoutText) {
        this.stdoutText = stdoutText;
    }

    public String getStderrText() {
        return stderrText;
    }

    public void setStderrText(String stderrText) {
        this.stderrText = stderrText;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
