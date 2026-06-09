package com.coderushoj.common.model;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("contest_registrations")
public class ContestRegistration {
    private Long contestId;
    private Long userId;
    private LocalDateTime registeredAt;

    public ContestRegistration() {
    }

    public ContestRegistration(Long contestId, Long userId, LocalDateTime registeredAt) {
        this.contestId = contestId;
        this.userId = userId;
        this.registeredAt = registeredAt;
    }

    public Long getContestId() {
        return contestId;
    }

    public void setContestId(Long contestId) {
        this.contestId = contestId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
}
