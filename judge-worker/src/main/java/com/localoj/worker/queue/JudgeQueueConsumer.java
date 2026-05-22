package com.localoj.worker.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localoj.common.queue.JudgeJob;
import com.localoj.worker.service.JudgeService;
import com.localoj.worker.service.SandboxSettingsProvider;
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
    private final String queueKey;
    private final ExecutorService executorService = Executors.newFixedThreadPool(32);
    private final AtomicInteger inFlightJobs = new AtomicInteger(0);

    public JudgeQueueConsumer(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            JudgeService judgeService,
            SandboxSettingsProvider sandboxSettingsProvider,
            @Value("${app.queue.submission-key}") String queueKey
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.judgeService = judgeService;
        this.sandboxSettingsProvider = sandboxSettingsProvider;
        this.queueKey = queueKey;
    }

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms}")
    public void poll() {
        int limit = sandboxSettingsProvider.current().effectiveConcurrentJobs();
        while (inFlightJobs.get() < limit) {
            String payload = redisTemplate.opsForList().rightPop(queueKey, Duration.ofMillis(150));
            if (payload == null) {
                return;
            }
            inFlightJobs.incrementAndGet();
            executorService.submit(() -> consume(payload));
        }
    }

    private void consume(String payload) {
        try {
            JudgeJob job = objectMapper.readValue(payload, JudgeJob.class);
            judgeService.judge(job.submissionId());
        } catch (Exception ex) {
            log.error("Failed to consume judge job: {}", payload, ex);
        } finally {
            inFlightJobs.decrementAndGet();
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
