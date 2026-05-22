package com.localoj.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaderboardService {
    private final JdbcTemplate jdbcTemplate;

    public LeaderboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LeaderboardRow> top(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
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
        List<LeaderboardRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new LeaderboardRow(
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
        ), boundedLimit);
        return rows;
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
