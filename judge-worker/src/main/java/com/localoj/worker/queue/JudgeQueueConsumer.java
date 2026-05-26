package com.localoj.worker.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localoj.common.queue.JudgeJob;
import com.localoj.worker.service.JudgeService;
import com.localoj.worker.service.SandboxSettingsProvider;
import com.localoj.worker.service.WorkerSystemLogService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class JudgeQueueConsumer {
    private static final Logger log = LoggerFactory.getLogger(JudgeQueueConsumer.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JudgeService judgeService;
    private final SandboxSettingsProvider sandboxSettingsProvider;
    private final WorkerSystemLogService systemLogService;
    private final String queueKey;
    private final String processingKey;
    private final String dlqKey;
    private final ExecutorService executorService = Executors.newFixedThreadPool(32);
    private final AtomicInteger inFlightJobs = new AtomicInteger(0);

    public JudgeQueueConsumer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JudgeService judgeService,
            SandboxSettingsProvider sandboxSettingsProvider,
            WorkerSystemLogService systemLogService,
            @Value("${app.queue.submission-key}") String queueKey,
            @Value("${app.queue.processing-key}") String processingKey,
            @Value("${app.queue.dlq-key}") String dlqKey
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.judgeService = judgeService;
        this.sandboxSettingsProvider = sandboxSettingsProvider;
        this.systemLogService = systemLogService;
        this.queueKey = queueKey;
        this.processingKey = processingKey;
        this.dlqKey = dlqKey;
    }

    @PostConstruct
    public void recoverProcessingQueue() {
        long recovered = 0;
        while (true) {
            String payload = redisTemplate.opsForList().rightPop(processingKey);
            if (payload == null) {
                break;
            }
            redisTemplate.opsForList().leftPush(queueKey, payload);
            recovered++;
        }
        if (recovered > 0) {
            systemLogService.warn(
                    "judge-queue",
                    "processing_recovered",
                    "worker 启动时恢复未确认判题任务",
                    null,
                    null,
                    null,
                    "processingKey=" + processingKey + "; recovered=" + recovered
            );
        }
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms}")
    public void poll() {
        int limit = sandboxSettingsProvider.current().effectiveConcurrentJobs();
        while (inFlightJobs.get() < limit) {
            String payload = redisTemplate.opsForList().rightPopAndLeftPush(queueKey, processingKey, Duration.ofMillis(150));
            if (payload == null) {
                return;
            }
            inFlightJobs.incrementAndGet();
            executorService.submit(() -> consume(payload));
        }
    }

    private void consume(String payload) {
        JudgeJob job = null;
        try {
            job = objectMapper.readValue(payload, JudgeJob.class);
        } catch (Exception ex) {
            log.error("Failed to parse judge job payload: {}", payload, ex);
            try {
                redisTemplate.opsForList().leftPush(dlqKey, payload);
                ackProcessing(payload);
                systemLogService.error("judge-queue", "job_corrupted", "判题任务载荷解析失败，已死信", null, null, null, ex);
            } catch (Exception redisEx) {
                log.error("Failed to push corrupted job to DLQ", redisEx);
            }
            inFlightJobs.decrementAndGet();
            return;
        }

        Long submissionId = job.submissionId();
        String retryKey = "judge:retry:" + submissionId;

        try {
            systemLogService.info("judge-queue", "job_consumed", "判题任务已被 worker 消费", submissionId, null, null, "queue=" + queueKey);
            judgeService.judge(submissionId);
            redisTemplate.delete(retryKey);
            ackProcessing(payload);
        } catch (Exception ex) {
            log.error("Failed to judge submission {}: {}", submissionId, ex.getMessage(), ex);
            try {
                String attemptStr = redisTemplate.opsForValue().get(retryKey);
                int attempts = attemptStr == null ? 1 : Integer.parseInt(attemptStr);

                if (attempts <= 3) {
                    long delayMs = attempts * 1000L;
                    systemLogService.warn(
                            "judge-queue",
                            "job_retry",
                            "判题发生异常，系统开始第 " + attempts + " 次重试",
                            submissionId,
                            null,
                            null,
                            "attempts=" + attempts + "; backoff=" + delayMs + "ms; error=" + ex.getMessage()
                    );
                    Thread.sleep(delayMs);
                    redisTemplate.opsForValue().increment(retryKey);
                    redisTemplate.expire(retryKey, Duration.ofHours(1));
                    redisTemplate.opsForList().leftPush(queueKey, payload);
                    ackProcessing(payload);
                } else {
                    systemLogService.error(
                            "judge-queue",
                            "job_dlq",
                            "判题任务重试次数超限，移入死信队列",
                            submissionId,
                            null,
                            null,
                            ex
                    );
                    redisTemplate.opsForList().leftPush(dlqKey, payload);
                    judgeService.failSubmissionPermanently(submissionId, "系统在重试 3 次后仍失败: " + ex.getMessage(), ex);
                    redisTemplate.delete(retryKey);
                    ackProcessing(payload);
                }
            } catch (Exception redisEx) {
                log.error("Failed in retry/dlq processing for submission {}", submissionId, redisEx);
            }
        } finally {
            inFlightJobs.decrementAndGet();
        }
    }

    private void ackProcessing(String payload) {
        try {
            redisTemplate.opsForList().remove(processingKey, 1, payload);
        } catch (Exception ex) {
            log.warn("Failed to ack judge job from processing queue {}", processingKey, ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
