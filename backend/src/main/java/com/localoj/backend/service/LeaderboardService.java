package com.localoj.backend.service;

import com.localoj.common.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Service
public class LeaderboardService {
    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final String LEADERBOARD_CACHE_KEY = "cache:leaderboard:latest";
    private static final long CACHE_TTL_SECONDS = 30;

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
            String cachedJson = redisTemplate.opsForValue().get(LEADERBOARD_CACHE_KEY);
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
                redisTemplate.opsForValue().set(LEADERBOARD_CACHE_KEY, json, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to write leaderboard to Redis cache", e);
            }
        }

        return rows == null ? Collections.emptyList() : rows;
    }

    public void evictCache() {
        try {
            redisTemplate.delete(LEADERBOARD_CACHE_KEY);
        } catch (Exception e) {
            log.error("Failed to evict leaderboard cache", e);
        }
    }

    public long getLastUpdatedTime() {
        // Return the current time aligned to 10 seconds as the version
        return (System.currentTimeMillis() / 10000L) * 10000L;
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

    @Scheduled(cron = "0 0 0 * * SUN")
    @Transactional
    public void snapshotRanks() {
        log.info("Generating weekly leaderboard rank snapshot...");
        List<LeaderboardRow> currentLeaderboard = queryLeaderboardFromDb(10000);
        try {
            jdbcTemplate.update("DELETE FROM user_rank_snapshots");
            if (!currentLeaderboard.isEmpty()) {
                String insertSql = "INSERT INTO user_rank_snapshots (user_id, prev_rank) VALUES (?, ?)";
                List<Object[]> batchArgs = new ArrayList<>();
                for (LeaderboardRow row : currentLeaderboard) {
                    batchArgs.add(new Object[]{row.userId(), row.rank()});
                }
                jdbcTemplate.batchUpdate(insertSql, batchArgs);
            }
            evictCache();
            log.info("Successfully saved {} users' rank snapshots.", currentLeaderboard.size());
        } catch (RuntimeException ex) {
            log.error("Failed to save leaderboard weekly rank snapshots", ex);
            throw ex;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initSnapshotsIfEmpty() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_rank_snapshots", Integer.class);
            if (count == null || count == 0) {
                log.info("Leaderboard rank snapshot table is empty. Running initial snapshot...");
                snapshotRanks();
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to check or initialize leaderboard rank snapshots (migration may not have run yet)", ex);
        }
    }

    private List<LeaderboardRow> queryLeaderboardFromDb(int limit) {
        String sql = """
                SELECT
                    u.id AS user_id,
                    u.username,
                    u.email,
                    u.display_name,
                    u.avatar_url,
                    u.student_no,
                    u.major,
                    COUNT(DISTINCT CASE WHEN s.verdict = 'AC' THEN s.problem_id END) AS accepted_count,
                    COUNT(s.id) AS submission_count,
                    MAX(CASE WHEN s.verdict = 'AC' THEN COALESCE(s.judged_at, s.created_at) END) AS last_accepted_at,
                    urs.prev_rank
                FROM users u
                LEFT JOIN submissions s ON s.user_id = u.id AND s.contest_id IS NULL
                LEFT JOIN user_rank_snapshots urs ON urs.user_id = u.id
                WHERE u.enabled = 1
                GROUP BY u.id, u.username, u.email, u.display_name, u.avatar_url, u.student_no, u.major, urs.prev_rank
                ORDER BY accepted_count DESC,
                         submission_count ASC,
                         CASE WHEN last_accepted_at IS NULL THEN 1 ELSE 0 END ASC,
                         last_accepted_at ASC,
                         u.id ASC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String avatarUrl = rs.getString("avatar_url");
            String email = rs.getString("email");
            String effectiveAvatar = User.getEffectiveAvatarUrl(avatarUrl, email);
            int currentRank = rowNum + 1;
            Object prevRankObj = rs.getObject("prev_rank");
            Integer rankChange = null;
            if (prevRankObj != null) {
                int prevRank = ((Number) prevRankObj).intValue();
                rankChange = prevRank - currentRank;
            }
            return new LeaderboardRow(
                    currentRank,
                    rs.getLong("user_id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    effectiveAvatar,
                    rs.getString("student_no"),
                    rs.getString("major"),
                    rs.getLong("accepted_count"),
                    rs.getLong("submission_count"),
                    toLocalDateTime(rs.getTimestamp("last_accepted_at")),
                    rankChange
            );
        }, limit);
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
            LocalDateTime lastAcceptedAt,
            Integer rankChange
    ) {
    }
}
