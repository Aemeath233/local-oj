package com.localoj.backend.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class SystemVersionService {
    private final JdbcTemplate jdbcTemplate;
    private final LeaderboardService leaderboardService;

    public SystemVersionService(JdbcTemplate jdbcTemplate, LeaderboardService leaderboardService) {
        this.jdbcTemplate = jdbcTemplate;
        this.leaderboardService = leaderboardService;
    }

    public Map<String, Long> getVersions() {
        Map<String, Long> versions = new HashMap<>();
        versions.put("problems", queryMaxTime("problems"));
        versions.put("trainings", queryMaxTime("training_sets"));
        versions.put("contests", queryMaxTime("contests"));
        versions.put("submissions", queryMaxTime("submissions"));
        versions.put("leaderboard", leaderboardService.getLastUpdatedTime());
        return versions;
    }

    private Long queryMaxTime(String tableName) {
        try {
            if (tableName.equals("submissions")) {
                // Aligned to 3-minute clock block (180,000 ms)
                return (System.currentTimeMillis() / 180000L) * 180000L;
            }
            String sql = "SELECT COALESCE(MAX(updated_at), '1970-01-01 00:00:00') FROM " + tableName;
            java.sql.Timestamp timestamp = jdbcTemplate.queryForObject(sql, java.sql.Timestamp.class);
            return timestamp != null ? timestamp.getTime() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
