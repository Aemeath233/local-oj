package com.localoj.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.localoj.common.enums.Role;

import java.time.LocalDateTime;

@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String displayName;
    private String avatarUrl;
    private String studentNo;
    private String major;
    private Role role;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer usernameChangeCountCurrentMonth;
    private LocalDateTime lastUsernameChangedAt;
    private LocalDateTime lastActiveAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public static String getEffectiveAvatarUrl(String avatarUrl, String email) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            if (email != null && !email.isBlank()) {
                String trimmedEmail = email.trim().toLowerCase();
                if (trimmedEmail.endsWith("@qq.com")) {
                    String qq = trimmedEmail.substring(0, trimmedEmail.indexOf("@qq.com"));
                    if (qq.matches("\\d+")) {
                        return "https://q1.qlogo.cn/g?b=qq&nk=" + qq + "&s=640";
                    }
                }
            }
        }
        return avatarUrl;
    }

    public String getAvatarUrl() {
        return getEffectiveAvatarUrl(this.avatarUrl, this.email);
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getUsernameChangeCountCurrentMonth() {
        return usernameChangeCountCurrentMonth;
    }

    public void setUsernameChangeCountCurrentMonth(Integer usernameChangeCountCurrentMonth) {
        this.usernameChangeCountCurrentMonth = usernameChangeCountCurrentMonth;
    }

    public LocalDateTime getLastUsernameChangedAt() {
        return lastUsernameChangedAt;
    }

    public void setLastUsernameChangedAt(LocalDateTime lastUsernameChangedAt) {
        this.lastUsernameChangedAt = lastUsernameChangedAt;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
