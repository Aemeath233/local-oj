package com.coderushoj.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import java.time.Duration;

@Component
public class WorkerMonitorService {
    private static final Logger log = LoggerFactory.getLogger(WorkerMonitorService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String workerId;
    private final String redisKey;

    public WorkerMonitorService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.worker.id:}") String configuredWorkerId
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        String hostIdent = configuredWorkerId;
        if (hostIdent == null || hostIdent.isBlank()) {
            hostIdent = "worker";
            try {
                hostIdent = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                try {
                    hostIdent = java.net.InetAddress.getLocalHost().getHostAddress();
                } catch (Exception e) {
                    String envHost = System.getenv("HOSTNAME");
                    if (envHost != null && !envHost.isBlank()) {
                        hostIdent = envHost;
                    }
                }
            }
        }
        if (hostIdent == null || hostIdent.isBlank()) {
            hostIdent = "worker-default";
        }
        this.workerId = hostIdent.replaceAll("[^a-zA-Z0-9.-]", "_");
        this.redisKey = "monitor:worker:" + this.workerId;
        log.info("WorkerMonitorService initialized with worker ID: {}", this.workerId);
    }

    @Scheduled(fixedRate = 5000)
    public void reportStatus() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("workerId", workerId);
            stats.put("timestamp", System.currentTimeMillis());

            // OS MXBean
            OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
            stats.put("osName", osMXBean.getName());
            stats.put("osArch", osMXBean.getArch());
            stats.put("availableProcessors", osMXBean.getAvailableProcessors());

            if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sunOSMXBean = (com.sun.management.OperatingSystemMXBean) osMXBean;
                stats.put("systemCpuLoad", sunOSMXBean.getCpuLoad());
                stats.put("processCpuLoad", sunOSMXBean.getProcessCpuLoad());
                stats.put("totalPhysicalMemory", sunOSMXBean.getTotalMemorySize());
                stats.put("freePhysicalMemory", sunOSMXBean.getFreeMemorySize());
            }

            // Memory MXBean
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            stats.put("heapMemoryUsed", memoryMXBean.getHeapMemoryUsage().getUsed());
            stats.put("heapMemoryMax", memoryMXBean.getHeapMemoryUsage().getMax());
            stats.put("heapMemoryCommitted", memoryMXBean.getHeapMemoryUsage().getCommitted());
            stats.put("nonHeapMemoryUsed", memoryMXBean.getNonHeapMemoryUsage().getUsed());

            // Thread MXBean
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            stats.put("threadCount", threadMXBean.getThreadCount());
            stats.put("peakThreadCount", threadMXBean.getPeakThreadCount());

            // Runtime MXBean
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            stats.put("uptime", runtimeMXBean.getUptime());
            stats.put("startTime", runtimeMXBean.getStartTime());

            String payload = objectMapper.writeValueAsString(stats);
            redisTemplate.opsForValue().set(redisKey, payload, Duration.ofSeconds(15));
        } catch (Exception e) {
            log.warn("Failed to report worker status to Redis", e);
        }
    }
}
