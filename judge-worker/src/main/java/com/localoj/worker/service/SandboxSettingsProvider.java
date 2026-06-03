package com.localoj.worker.service;

import com.localoj.common.mapper.SandboxSettingMapper;
import com.localoj.common.model.SandboxSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SandboxSettingsProvider {
    private static final Logger log = LoggerFactory.getLogger(SandboxSettingsProvider.class);
    private static final long SETTINGS_ID = 1L;

    private final SandboxSettingMapper sandboxSettingMapper;
    private volatile Settings cachedSettings;
    private volatile long lastFetchTime;
    private static final long CACHE_DURATION_MS = 5000L;

    public SandboxSettingsProvider(SandboxSettingMapper sandboxSettingMapper) {
        this.sandboxSettingMapper = sandboxSettingMapper;
    }

    public Settings current() {
        long now = System.currentTimeMillis();
        Settings localCached = cachedSettings;
        if (localCached != null && (now - lastFetchTime < CACHE_DURATION_MS)) {
            return localCached;
        }
        synchronized (this) {
            localCached = cachedSettings;
            if (localCached != null && (now - lastFetchTime < CACHE_DURATION_MS)) {
                return localCached;
            }
            try {
                SandboxSetting setting = sandboxSettingMapper.selectById(SETTINGS_ID);
                if (setting != null) {
                    localCached = Settings.from(setting);
                } else {
                    localCached = Settings.defaults();
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to read sandbox settings, falling back to defaults", ex);
                localCached = Settings.defaults();
            }
            cachedSettings = localCached;
            lastFetchTime = System.currentTimeMillis();
            return localCached;
        }
    }

    public record Settings(
            int workerThreads,
            int maxConcurrentRuns,
            int compileTimeoutMs,
            int defaultOutputLimitKb,
            int maxProcessCount
    ) {
        static Settings from(SandboxSetting setting) {
            return new Settings(
                    clamp(setting.getWorkerThreads(), 1, 32, 1),
                    clamp(setting.getMaxConcurrentRuns(), 1, 32, 1),
                    clamp(setting.getCompileTimeoutMs(), 1000, 120000, 10000),
                    clamp(setting.getDefaultOutputLimitKb(), 64, 262144, 1024),
                    clamp(setting.getMaxProcessCount(), 1, 256, 50)
            );
        }

        static Settings defaults() {
            return new Settings(1, 1, 10000, 1024, 50);
        }

        public int effectiveConcurrentJobs() {
            return Math.max(1, Math.min(workerThreads, maxConcurrentRuns));
        }

        public long outputLimitBytes() {
            return defaultOutputLimitKb * 1024L;
        }

        public long compileCpuLimitNs() {
            return compileTimeoutMs * 1_000_000L;
        }

        public long compileClockLimitNs() {
            return compileCpuLimitNs() * 2 + 1_000_000_000L;
        }

        private static int clamp(Integer value, int min, int max, int fallback) {
            int resolved = value == null ? fallback : value;
            return Math.max(min, Math.min(max, resolved));
        }
    }
}
