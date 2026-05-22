package com.localoj.backend.gojudge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoJudgeResult {
    private String status;
    private String error;
    private Integer exitStatus;
    private Long time;
    private Long memory;
    private Long runTime;
    private Map<String, String> files;
    private Map<String, String> fileIds;
    private List<GoJudgeFileError> fileError;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getExitStatus() {
        return exitStatus;
    }

    public void setExitStatus(Integer exitStatus) {
        this.exitStatus = exitStatus;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public Long getMemory() {
        return memory;
    }

    public void setMemory(Long memory) {
        this.memory = memory;
    }

    public Long getRunTime() {
        return runTime;
    }

    public void setRunTime(Long runTime) {
        this.runTime = runTime;
    }

    public Map<String, String> getFiles() {
        return files;
    }

    public void setFiles(Map<String, String> files) {
        this.files = files;
    }

    public Map<String, String> getFileIds() {
        return fileIds;
    }

    public void setFileIds(Map<String, String> fileIds) {
        this.fileIds = fileIds;
    }

    public List<GoJudgeFileError> getFileError() {
        return fileError;
    }

    public void setFileError(List<GoJudgeFileError> fileError) {
        this.fileError = fileError;
    }
}
