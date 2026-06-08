package com.localoj.backend.service;

import com.localoj.common.mapper.SandboxSettingMapper;
import com.localoj.common.model.SandboxSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SandboxSettingsService {
    public static final long SETTINGS_ID = 1L;

    private final SandboxSettingMapper sandboxSettingMapper;

    public SandboxSettingsService(SandboxSettingMapper sandboxSettingMapper) {
        this.sandboxSettingMapper = sandboxSettingMapper;
    }

    public SandboxSetting requireSettings() {
        SandboxSetting setting = sandboxSettingMapper.selectById(SETTINGS_ID);
        if (setting != null) {
            return setting;
        }
        SandboxSetting created = defaults();
        sandboxSettingMapper.insert(created);
        return created;
    }

    public SandboxSettingsView view() {
        return SandboxSettingsView.from(requireSettings());
    }

    @Transactional
    public SandboxSettingsView update(UpdateSandboxSettingsCommand command) {
        SandboxSetting setting = requireSettings();
        setting.setWorkerThreads(clamp(command.workerThreads(), 1, 32, 1));
        setting.setMaxConcurrentRuns(clamp(command.maxConcurrentRuns(), 1, 32, 1));
        setting.setCompileTimeoutMs(clamp(command.compileTimeoutMs(), 1000, 120000, 10000));
        setting.setDefaultOutputLimitKb(clamp(command.defaultOutputLimitKb(), 64, 262144, 1024));
        setting.setMaxProcessCount(clamp(command.maxProcessCount(), 1, 256, 50));
        setting.setCaseConcurrentRuns(clamp(command.caseConcurrentRuns(), 1, 32, 1));
        setting.setUpdatedAt(LocalDateTime.now());
        sandboxSettingMapper.updateById(setting);
        return SandboxSettingsView.from(setting);
    }

    private SandboxSetting defaults() {
        LocalDateTime now = LocalDateTime.now();
        SandboxSetting setting = new SandboxSetting();
        setting.setId(SETTINGS_ID);
        setting.setWorkerThreads(1);
        setting.setMaxConcurrentRuns(1);
        setting.setCompileTimeoutMs(10000);
        setting.setDefaultOutputLimitKb(1024);
        setting.setMaxProcessCount(50);
        setting.setCaseConcurrentRuns(1);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    public record UpdateSandboxSettingsCommand(
            Integer workerThreads,
            Integer maxConcurrentRuns,
            Integer compileTimeoutMs,
            Integer defaultOutputLimitKb,
            Integer maxProcessCount,
            Integer caseConcurrentRuns
    ) {
    }

    public record SandboxSettingsView(
            Integer workerThreads,
            Integer maxConcurrentRuns,
            Integer compileTimeoutMs,
            Integer defaultOutputLimitKb,
            Integer maxProcessCount,
            Integer caseConcurrentRuns
    ) {
        static SandboxSettingsView from(SandboxSetting setting) {
            return new SandboxSettingsView(
                    setting.getWorkerThreads(),
                    setting.getMaxConcurrentRuns(),
                    setting.getCompileTimeoutMs(),
                    setting.getDefaultOutputLimitKb(),
                    setting.getMaxProcessCount(),
                    setting.getCaseConcurrentRuns()
            );
        }
    }
}
