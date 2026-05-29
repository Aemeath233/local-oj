package com.localoj.backend.service;

import com.localoj.common.mapper.SystemSettingMapper;
import com.localoj.common.model.SystemSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class SystemSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final SystemSettingMapper systemSettingMapper;
    private volatile List<String> allowedOriginsCache = null;

    public SystemSettingsService(SystemSettingMapper systemSettingMapper) {
        this.systemSettingMapper = systemSettingMapper;
    }

    @PostConstruct
    public void init() {
        refreshCache();
    }

    public SystemSetting requireSettings() {
        SystemSetting setting = systemSettingMapper.selectById(SETTINGS_ID);
        if (setting != null) {
            return setting;
        }
        SystemSetting created = defaults();
        systemSettingMapper.insert(created);
        return created;
    }

    public SystemSettingsView view() {
        return SystemSettingsView.from(requireSettings());
    }

    @Transactional
    public SystemSettingsView update(UpdateSystemSettingsCommand command) {
        SystemSetting setting = requireSettings();
        String allowed = command.allowedOrigins();
        setting.setAllowedOrigins(allowed == null ? "" : allowed.trim());
        setting.setUpdatedAt(LocalDateTime.now());
        systemSettingMapper.updateById(setting);
        
        refreshCache();
        
        return SystemSettingsView.from(setting);
    }

    public List<String> getCachedAllowedOrigins() {
        if (allowedOriginsCache == null) {
            refreshCache();
        }
        return allowedOriginsCache;
    }

    private synchronized void refreshCache() {
        try {
            SystemSetting setting = requireSettings();
            String originsStr = setting.getAllowedOrigins();
            if (originsStr == null || originsStr.isBlank()) {
                allowedOriginsCache = Collections.emptyList();
            } else {
                allowedOriginsCache = Arrays.stream(originsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        } catch (Exception e) {
            // Fallback default in case database is not migrated yet during context load
            allowedOriginsCache = Arrays.asList(
                    "http://localhost:5173",
                    "http://127.0.0.1:5173",
                    "http://192.168.*.*:5173",
                    "http://10.*.*.*:5173"
            );
        }
    }

    private SystemSetting defaults() {
        LocalDateTime now = LocalDateTime.now();
        SystemSetting setting = new SystemSetting();
        setting.setId(SETTINGS_ID);
        setting.setAllowedOrigins("http://localhost:5173,http://127.0.0.1:5173,http://192.168.*.*:5173,http://10.*.*.*:5173");
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }

    public record UpdateSystemSettingsCommand(String allowedOrigins) {
    }

    public record SystemSettingsView(String allowedOrigins) {
        static SystemSettingsView from(SystemSetting setting) {
            return new SystemSettingsView(setting.getAllowedOrigins());
        }
    }
}
