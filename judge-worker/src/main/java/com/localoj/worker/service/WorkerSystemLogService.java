package com.localoj.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class WorkerSystemLogService {
    private static final Logger log = LoggerFactory.getLogger(WorkerSystemLogService.class);

    public WorkerSystemLogService() {
        // Empty constructor, no database dependencies!
    }

    public void info(String module, String event, String message, Long submissionId, Long problemId, Long userId, String details) {
        log.info("[{}] [{}] - {} | subId={}, probId={}, userId={}, details={}", 
            module, event, message, submissionId, problemId, userId, details);
    }

    public void warn(String module, String event, String message, Long submissionId, Long problemId, Long userId, String details) {
        log.warn("[{}] [{}] - {} | subId={}, probId={}, userId={}, details={}", 
            module, event, message, submissionId, problemId, userId, details);
    }

    public void error(String module, String event, String message, Long submissionId, Long problemId, Long userId, Throwable throwable) {
        log.error("[{}] [{}] - {} | subId={}, probId={}, userId={} | Exception: {}", 
            module, event, message, submissionId, problemId, userId, stackTrace(throwable));
    }

    public void errorDetails(String module, String event, String message, Long submissionId, Long problemId, Long userId, String details) {
        log.error("[{}] [{}] - {} | subId={}, probId={}, userId={}, details={}", 
            module, event, message, submissionId, problemId, userId, details);
    }

    private static String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }
}
