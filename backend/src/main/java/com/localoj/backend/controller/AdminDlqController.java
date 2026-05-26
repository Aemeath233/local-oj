package com.localoj.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localoj.backend.api.ApiResponse;
import com.localoj.common.enums.SubmissionStatus;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.model.Submission;
import com.localoj.common.queue.JudgeJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dlq")
public class AdminDlqController {
    private final StringRedisTemplate redisTemplate;
    private final SubmissionMapper submissionMapper;
    private final ObjectMapper objectMapper;
    private final String submissionQueueKey;
    private final String dlqKey;

    public AdminDlqController(
            StringRedisTemplate redisTemplate,
            SubmissionMapper submissionMapper,
            ObjectMapper objectMapper,
            @Value("${app.queue.submission-key}") String submissionQueueKey,
            @Value("${app.queue.dlq-key}") String dlqKey
    ) {
        this.redisTemplate = redisTemplate;
        this.submissionMapper = submissionMapper;
        this.objectMapper = objectMapper;
        this.submissionQueueKey = submissionQueueKey;
        this.dlqKey = dlqKey;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getStats() {
        List<String> items = redisTemplate.opsForList().range(dlqKey, 0, -1);
        long size = items == null ? 0 : items.size();
        return ApiResponse.ok(Map.of(
                "size", size,
                "items", items == null ? List.of() : items
        ));
    }

    @PostMapping("/clear")
    public ApiResponse<Object> clear() {
        redisTemplate.delete(dlqKey);
        return ApiResponse.ok(null);
    }

    @PostMapping("/requeue")
    public ApiResponse<Map<String, Object>> requeue() {
        long requeueCount = 0;
        long failedCount = 0;
        Long initialSize = redisTemplate.opsForList().size(dlqKey);
        long itemsToProcess = initialSize == null ? 0 : initialSize;
        for (long i = 0; i < itemsToProcess; i++) {
            String payload = redisTemplate.opsForList().rightPop(dlqKey);
            if (payload == null) {
                break;
            }
            try {
                JudgeJob job = objectMapper.readValue(payload, JudgeJob.class);
                Long subId = job.submissionId();

                Submission sub = submissionMapper.selectById(subId);
                if (sub != null) {
                    sub.setStatus(SubmissionStatus.PENDING);
                    sub.setVerdict(null);
                    sub.setScore(0);
                    sub.setErrorMessage(null);
                    sub.setTimeMs(0L);
                    sub.setMemoryKb(0L);
                    submissionMapper.updateById(sub);
                }

                redisTemplate.delete("judge:retry:" + subId);
                redisTemplate.opsForList().leftPush(submissionQueueKey, payload);
                requeueCount++;
            } catch (Exception ex) {
                redisTemplate.opsForList().leftPush(dlqKey, payload);
                failedCount++;
            }
        }
        Long remaining = redisTemplate.opsForList().size(dlqKey);
        return ApiResponse.ok(Map.of(
                "requeued", requeueCount,
                "failed", failedCount,
                "remaining", remaining == null ? 0 : remaining
        ));
    }
}
