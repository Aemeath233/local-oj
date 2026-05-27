package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.LeaderboardService;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ApiResponse<List<LeaderboardService.LeaderboardRow>> top(
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return ApiResponse.ok(leaderboardService.top(limit));
    }

    @GetMapping("/my-rank")
    public ApiResponse<LeaderboardService.LeaderboardRow> myRank() {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        if (user == null) {
            return ApiResponse.ok(null);
        }
        return ApiResponse.ok(leaderboardService.myRank(user.id()));
    }
}
