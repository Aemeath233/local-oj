package com.coderushoj.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkerMonitorServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void reportStatusSavesTelemetryToRedis() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ObjectMapper objectMapper = new ObjectMapper();

        WorkerMonitorService service = new WorkerMonitorService(redisTemplate, objectMapper, "test-worker");

        // Trigger telemetry collection
        service.reportStatus();

        // Capture calls to Redis opsForValue().set(key, val, ttl)
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("monitor:worker:test-worker");
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofSeconds(15));

        // Deserialize value and verify contents
        Map<String, Object> telemetry = objectMapper.readValue(valueCaptor.getValue(), Map.class);
        assertThat(telemetry).containsEntry("workerId", "test-worker");
        assertThat(telemetry).containsKey("uptime");
        assertThat(telemetry).containsKey("heapMemoryUsed");
        assertThat(telemetry).containsKey("threadCount");
        assertThat(telemetry).containsKey("availableProcessors");
    }
}
