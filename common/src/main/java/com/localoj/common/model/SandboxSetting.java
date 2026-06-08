package com.localoj.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sandbox_settings")
public class SandboxSetting {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Integer workerThreads;
    private Integer maxConcurrentRuns;
    private Integer compileTimeoutMs;
    private Integer defaultOutputLimitKb;
    private Integer maxProcessCount;
    private Integer caseConcurrentRuns;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(Integer workerThreads) {
        this.workerThreads = workerThreads;
    }

    public Integer getMaxConcurrentRuns() {
        return maxConcurrentRuns;
    }

    public void setMaxConcurrentRuns(Integer maxConcurrentRuns) {
        this.maxConcurrentRuns = maxConcurrentRuns;
    }

    public Integer getCompileTimeoutMs() {
        return compileTimeoutMs;
    }

    public void setCompileTimeoutMs(Integer compileTimeoutMs) {
        this.compileTimeoutMs = compileTimeoutMs;
    }

    public Integer getDefaultOutputLimitKb() {
        return defaultOutputLimitKb;
    }

    public void setDefaultOutputLimitKb(Integer defaultOutputLimitKb) {
        this.defaultOutputLimitKb = defaultOutputLimitKb;
    }

    public Integer getMaxProcessCount() {
        return maxProcessCount;
    }

    public void setMaxProcessCount(Integer maxProcessCount) {
        this.maxProcessCount = maxProcessCount;
    }

    public Integer getCaseConcurrentRuns() {
        return caseConcurrentRuns;
    }

    public void setCaseConcurrentRuns(Integer caseConcurrentRuns) {
        this.caseConcurrentRuns = caseConcurrentRuns;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
