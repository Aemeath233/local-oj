package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/monitoring")
public class AdminMonitoringController {
    private static final Logger log = LoggerFactory.getLogger(AdminMonitoringController.class);

    private final StringRedisTemplate redisTemplate;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    private final String appDataRoot;
    private final String goJudgeBaseUrl;
    private final String submissionQueueKey;
    private final String dlqKey;

    public AdminMonitoringController(
            StringRedisTemplate redisTemplate,
            DataSource dataSource,
            ObjectMapper objectMapper,
            @Value("${app.data-root:/data}") String appDataRoot,
            @Value("${app.go-judge.base-url}") String goJudgeBaseUrl,
            @Value("${app.queue.submission-key}") String submissionQueueKey,
            @Value("${app.queue.dlq-key}") String dlqKey
    ) {
        this.redisTemplate = redisTemplate;
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.appDataRoot = appDataRoot;
        this.goJudgeBaseUrl = goJudgeBaseUrl;
        this.submissionQueueKey = submissionQueueKey;
        this.dlqKey = dlqKey;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. System / OS Info
        stats.put("system", getSystemStats());

        // 2. Backend JVM Info
        stats.put("jvm", getJvmStats());

        // 3. Database Pool Info (HikariCP)
        stats.put("dbPool", getDbPoolStats());

        // 4. Redis Status
        stats.put("redis", getRedisStats());

        // 5. Go-Judge Health
        stats.put("goJudge", getGoJudgeStats());

        // 6. Judge Queue & Active Workers
        stats.put("queue", getQueueStats());
        stats.put("workers", getWorkerNodesStats());

        return ApiResponse.ok(stats);
    }

    private Map<String, Object> getSystemStats() {
        Map<String, Object> sys = new LinkedHashMap<>();
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        sys.put("osName", osMXBean.getName());
        sys.put("osArch", osMXBean.getArch());
        sys.put("osVersion", osMXBean.getVersion());
        sys.put("availableProcessors", osMXBean.getAvailableProcessors());

        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOSMXBean = (com.sun.management.OperatingSystemMXBean) osMXBean;
            sys.put("systemCpuLoad", sunOSMXBean.getCpuLoad());
            sys.put("totalPhysicalMemory", sunOSMXBean.getTotalMemorySize());
            sys.put("freePhysicalMemory", sunOSMXBean.getFreeMemorySize());
        }

        // Disk Usage
        File root = new File(appDataRoot);
        if (!root.exists()) {
            root.mkdirs();
        }
        sys.put("diskTotal", root.getTotalSpace());
        sys.put("diskFree", root.getFreeSpace());
        sys.put("diskUsable", root.getUsableSpace());

        return sys;
    }

    private Map<String, Object> getJvmStats() {
        Map<String, Object> jvm = new LinkedHashMap<>();

        // Memory Usage
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        jvm.put("heapMemoryUsed", memoryMXBean.getHeapMemoryUsage().getUsed());
        jvm.put("heapMemoryMax", memoryMXBean.getHeapMemoryUsage().getMax());
        jvm.put("heapMemoryCommitted", memoryMXBean.getHeapMemoryUsage().getCommitted());
        jvm.put("nonHeapMemoryUsed", memoryMXBean.getNonHeapMemoryUsage().getUsed());

        // CPU Usage
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        if (osMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOSMXBean = (com.sun.management.OperatingSystemMXBean) osMXBean;
            jvm.put("processCpuLoad", sunOSMXBean.getProcessCpuLoad());
        }

        // Threads
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        jvm.put("threadCount", threadMXBean.getThreadCount());
        jvm.put("peakThreadCount", threadMXBean.getPeakThreadCount());
        jvm.put("daemonThreadCount", threadMXBean.getDaemonThreadCount());

        // Uptime
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        jvm.put("uptime", runtimeMXBean.getUptime());
        jvm.put("startTime", runtimeMXBean.getStartTime());

        // GC Stats
        long gcCount = 0;
        long gcTime = 0;
        for (GarbageCollectorMXBean gcMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gcMXBean.getCollectionCount();
            long time = gcMXBean.getCollectionTime();
            if (count > 0) gcCount += count;
            if (time > 0) gcTime += time;
        }
        jvm.put("gcCollectionCount", gcCount);
        jvm.put("gcCollectionTime", gcTime);

        return jvm;
    }

    private Map<String, Object> getDbPoolStats() {
        Map<String, Object> pool = new LinkedHashMap<>();
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hds = (HikariDataSource) dataSource;
            var mxBean = hds.getHikariPoolMXBean();
            if (mxBean != null) {
                pool.put("activeConnections", mxBean.getActiveConnections());
                pool.put("idleConnections", mxBean.getIdleConnections());
                pool.put("totalConnections", mxBean.getTotalConnections());
                pool.put("threadsAwaitingConnection", mxBean.getThreadsAwaitingConnection());
            } else {
                pool.put("activeConnections", 0);
                pool.put("idleConnections", 0);
                pool.put("totalConnections", 0);
                pool.put("threadsAwaitingConnection", 0);
            }
            pool.put("maxPoolSize", hds.getMaximumPoolSize());
            pool.put("driverClassName", hds.getDriverClassName());
            pool.put("jdbcUrl", hds.getJdbcUrl());
        } else {
            pool.put("status", "Unknown DataSource type: " + dataSource.getClass().getName());
        }
        return pool;
    }

    private Map<String, Object> getRedisStats() {
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            long start = System.currentTimeMillis();
            redisTemplate.getConnectionFactory().getConnection().ping();
            long pingMs = System.currentTimeMillis() - start;
            redis.put("status", "UP");
            redis.put("pingMs", pingMs);

            Properties info = redisTemplate.execute((RedisConnection conn) -> conn.serverCommands().info());
            if (info != null) {
                redis.put("version", info.getProperty("redis_version", "Unknown"));
                redis.put("usedMemory", Long.parseLong(info.getProperty("used_memory", "0")));
                redis.put("connectedClients", Integer.parseInt(info.getProperty("connected_clients", "0")));
            }
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("error", e.getMessage());
        }
        return redis;
    }

    private Map<String, Object> getGoJudgeStats() {
        Map<String, Object> goJudge = new LinkedHashMap<>();
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(2000))
                    .build();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(goJudgeBaseUrl + "/version"))
                    .timeout(java.time.Duration.ofMillis(2000))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                goJudge.put("status", "UP");
                goJudge.put("version", response.body());
            } else {
                goJudge.put("status", "DOWN");
                goJudge.put("statusCode", response.statusCode());
            }
        } catch (Exception e) {
            goJudge.put("status", "DOWN");
            goJudge.put("error", e.getMessage());
        }
        return goJudge;
    }

    private Map<String, Object> getQueueStats() {
        Map<String, Object> q = new LinkedHashMap<>();
        try {
            Long size = redisTemplate.opsForList().size(submissionQueueKey);
            Long dlqSize = redisTemplate.opsForList().size(dlqKey);
            q.put("size", size == null ? 0L : size);
            q.put("dlqSize", dlqSize == null ? 0L : dlqSize);
            q.put("status", "UP");
        } catch (Exception e) {
            q.put("status", "DOWN");
            q.put("error", e.getMessage());
        }
        return q;
    }

    private List<Map<String, Object>> getWorkerNodesStats() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys("monitor:worker:*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    String val = redisTemplate.opsForValue().get(key);
                    if (val != null) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> stats = objectMapper.readValue(val, Map.class);
                            list.add(stats);
                        } catch (Exception e) {
                            log.warn("Failed to parse worker stats from Redis for key: {}", key, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read worker stats from Redis", e);
        }
        return list;
    }
}
