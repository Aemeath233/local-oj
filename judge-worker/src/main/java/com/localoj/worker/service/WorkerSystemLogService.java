package com.localoj.worker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;

@Service
public class WorkerSystemLogService {
    private static final Logger log = LoggerFactory.getLogger(WorkerSystemLogService.class);
    private static final String REDIS_LOG_KEY = "localoj:settings:logging_enabled";

    private final StringRedisTemplate redisTemplate;
    private volatile boolean loggingEnabled = false;

    public WorkerSystemLogService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        applyLogLevel(false); // Default to off at start
        syncLoggingLevel();   // Try to sync immediately
    }

    private void applyLogLevel(boolean enabled) {
        try {
            ch.qos.logback.classic.LoggerContext loggerContext = 
                (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
            
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (rootLogger != null) {
                rootLogger.setLevel(enabled ? ch.qos.logback.classic.Level.INFO : ch.qos.logback.classic.Level.OFF);
            }
            
            ch.qos.logback.classic.Logger logger = loggerContext.getLogger("com.localoj");
            if (logger != null) {
                logger.setLevel(enabled ? ch.qos.logback.classic.Level.INFO : ch.qos.logback.classic.Level.OFF);
            }
        } catch (Throwable ignored) {}
    }

    @Scheduled(fixedDelay = 2000)
    public void syncLoggingLevel() {
        try {
            String val = redisTemplate.opsForValue().get(REDIS_LOG_KEY);
            if (val != null) {
                boolean enabled = Boolean.parseBoolean(val);
                if (enabled != this.loggingEnabled) {
                    this.loggingEnabled = enabled;
                    applyLogLevel(enabled);
                }
            } else {
                applyLogLevel(this.loggingEnabled);
            }
        } catch (Throwable ignored) {}
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

    public void errorDetails(String module, String event, String message, Long submissionId, Long problemId, Long userId, String details) {
        if (loggingEnabled) {
            log.error("[{}] [{}] - {} | subId={}, probId={}, userId={}, details={}", 
                module, event, message, submissionId, problemId, userId, details);
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
