package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ListOperations;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminMonitoringControllerTest {

    @Test
    @SuppressWarnings("unchecked")
    void getStatsReturnsStatsSuccessfully() {
        // Mock Redis Template and Connections
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnectionFactory connFactory = mock(RedisConnectionFactory.class);
        RedisConnection conn = mock(RedisConnection.class);
        RedisServerCommands serverCommands = mock(RedisServerCommands.class);
        
        when(redisTemplate.getConnectionFactory()).thenReturn(connFactory);
        when(connFactory.getConnection()).thenReturn(conn);
        when(conn.serverCommands()).thenReturn(serverCommands);
        when(conn.ping()).thenReturn("PONG");
        
        // Mock Redis info properties
        Properties redisInfo = new Properties();
        redisInfo.setProperty("redis_version", "8.8.0");
        redisInfo.setProperty("used_memory", "1048576");
        redisInfo.setProperty("connected_clients", "2");
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(redisInfo);
        
        ListOperations<String, String> listOps = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(listOps.size(any())).thenReturn(0L);
        when(redisTemplate.keys(any())).thenReturn(Set.of());

        // Mock Hikari DataSource and HikariPoolMXBean
        HikariDataSource dataSource = mock(HikariDataSource.class);
        HikariPoolMXBean mxBean = mock(HikariPoolMXBean.class);
        when(dataSource.getHikariPoolMXBean()).thenReturn(mxBean);
        when(dataSource.getMaximumPoolSize()).thenReturn(30);
        when(mxBean.getActiveConnections()).thenReturn(5);
        when(mxBean.getIdleConnections()).thenReturn(10);
        when(mxBean.getTotalConnections()).thenReturn(15);
        when(mxBean.getThreadsAwaitingConnection()).thenReturn(0);

        ObjectMapper objectMapper = new ObjectMapper();

        AdminMonitoringController controller = new AdminMonitoringController(
                redisTemplate,
                dataSource,
                objectMapper,
                "/data",
                "http://localhost:5050",
                "judge:queue",
                "judge:dlq"
        );
        
        ApiResponse<Map<String, Object>> response = controller.getStats();
        assertNotNull(response);
        
        Map<String, Object> data = response.data();
        assertNotNull(data);
        
        assertTrue(data.containsKey("system"));
        assertTrue(data.containsKey("jvm"));
        assertTrue(data.containsKey("dbPool"));
        assertTrue(data.containsKey("redis"));
        assertTrue(data.containsKey("goJudge"));
        assertTrue(data.containsKey("queue"));
        assertTrue(data.containsKey("workers"));
        
        Map<String, Object> dbPool = (Map<String, Object>) data.get("dbPool");
        assertEquals(30, dbPool.get("maxPoolSize"));
        assertEquals(5, dbPool.get("activeConnections"));
        
        Map<String, Object> redis = (Map<String, Object>) data.get("redis");
        assertEquals("UP", redis.get("status"));
        assertEquals("8.8.0", redis.get("version"));
    }
}
