package com.localoj.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class LeaderboardService {
    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final String CACHE_KEY = "cache:leaderboard:top200";
    private static final long CACHE_TTL_SECONDS = 3;

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LeaderboardService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private List<LeaderboardRow> getAllLeaderboard() {
        try {
            String cachedJson = redisTemplate.opsForValue().get("cache:leaderboard:all1000");
            if (cachedJson != null && !cachedJson.isBlank()) {
                List<LeaderboardRow> cachedRows = objectMapper.readValue(
                        cachedJson, 
                        new TypeReference<List<LeaderboardRow>>() {}
                );
                if (cachedRows != null && !cachedRows.isEmpty()) {
                    return cachedRows;
                }
            }
        } catch (Exception e) {
            log.error("Failed to read leaderboard from Redis cache", e);
        }

        List<LeaderboardRow> rows = queryLeaderboardFromDb(1000);

        if (rows != null && !rows.isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(rows);
                redisTemplate.opsForValue().set("cache:leaderboard:all1000", json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to write leaderboard to Redis cache", e);
            }
        }

        return rows == null ? Collections.emptyList() : rows;
    }

    public List<LeaderboardRow> top(int limit) {
        List<LeaderboardRow> all = getAllLeaderboard();
        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        return all.subList(0, Math.min(boundedLimit, all.size()));
    }

    public LeaderboardRow myRank(Long currentUserId) {
        if (currentUserId == null) {
            return null;
        }
        List<LeaderboardRow> all = getAllLeaderboard();
        for (LeaderboardRow row : all) {
            if (row.userId().equals(currentUserId)) {
                return row;
            }
        }
        return null;
    }

    private List<LeaderboardRow> queryLeaderboardFromDb(int limit) {
        String sql = """
                SELECT
                    u.id AS user_id,
                    u.username,
                    u.display_name,
                    u.avatar_url,
                    u.student_no,
                    u.major,
                    COUNT(DISTINCT CASE WHEN s.verdict = 'AC' THEN s.problem_id END) AS accepted_count,
                    COUNT(s.id) AS submission_count,
                    MAX(CASE WHEN s.verdict = 'AC' THEN COALESCE(s.judged_at, s.created_at) END) AS last_accepted_at
                FROM users u
                LEFT JOIN submissions s ON s.user_id = u.id
                WHERE u.enabled = 1
                GROUP BY u.id, u.username, u.display_name, u.avatar_url, u.student_no, u.major
                ORDER BY accepted_count DESC,
                         submission_count ASC,
                         CASE WHEN last_accepted_at IS NULL THEN 1 ELSE 0 END ASC,
                         last_accepted_at ASC,
                         u.id ASC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new LeaderboardRow(
                rowNum + 1,
                rs.getLong("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("avatar_url"),
                rs.getString("student_no"),
                rs.getString("major"),
                rs.getLong("accepted_count"),
                rs.getLong("submission_count"),
                toLocalDateTime(rs.getTimestamp("last_accepted_at"))
        ), limit);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record LeaderboardRow(
            int rank,
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            String studentNo,
            String major,
            Long acceptedCount,
            Long submissionCount,
            LocalDateTime lastAcceptedAt
    ) {
    }
}
