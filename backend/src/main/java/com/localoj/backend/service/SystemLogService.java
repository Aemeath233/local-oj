package com.localoj.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class SystemLogService {
    private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);
    private boolean loggingEnabled = true;

    public SystemLogService() {
        // Empty constructor, no database dependencies!
    }

    public boolean isLoggingEnabled() {
        return this.loggingEnabled;
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
        try {
            ch.qos.logback.classic.LoggerContext loggerContext = 
                (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger("com.localoj");
            if (logger != null) {
                logger.setLevel(enabled ? ch.qos.logback.classic.Level.INFO : ch.qos.logback.classic.Level.OFF);
            }
            log.info("System logging state changed dynamically: {}", enabled ? "ENABLED (INFO)" : "DISABLED (OFF)");
        } catch (Throwable t) {
            log.warn("Failed to dynamically set Logback log level: {}", t.getMessage());
        }
    }

    public void info(String module, String event, String message) {
        if (loggingEnabled) {
            log.info("[{}] [{}] - {}", module, event, message);
        }
    }

    public void info(String module, String event, String message, Long submissionId, Long problemId, Long userId, String details) {
        if (loggingEnabled) {
            log.info("[{}] [{}] - {} | subId={}, probId={}, userId={}, details={}", 
                module, event, message, submissionId, problemId, userId, details);
        }
    }

    public void warn(String module, String event, String message, Long submissionId, Long problemId, Long userId, String details) {
        if (loggingEnabled) {
            log.warn("[{}] [{}] - {} | subId={}, probId={}, userId={}, details={}", 
                module, event, message, submissionId, problemId, userId, details);
        }
    }

    public void error(String module, String event, String message, Long submissionId, Long problemId, Long userId, Throwable throwable) {
        if (loggingEnabled) {
            log.error("[{}] [{}] - {} | subId={}, probId={}, userId={} | Exception: {}", 
                module, event, message, submissionId, problemId, userId, stackTrace(throwable));
        }
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
