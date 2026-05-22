package com.localoj.backend.service;

import com.localoj.common.mapper.LlmSettingMapper;
import com.localoj.common.model.LlmSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LlmSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final LlmSettingMapper llmSettingMapper;

    public LlmSettingsService(LlmSettingMapper llmSettingMapper) {
        this.llmSettingMapper = llmSettingMapper;
    }

    public LlmSetting requireSettings() {
        LlmSetting setting = llmSettingMapper.selectById(SETTINGS_ID);
        if (setting != null) {
            return setting;
        }
        LlmSetting created = defaults();
        llmSettingMapper.insert(created);
        return created;
    }

    public LlmSettingsView view() {
        return LlmSettingsView.from(requireSettings());
    }

    @Transactional
    public LlmSettingsView update(UpdateLlmSettingsCommand command) {
        LlmSetting setting = requireSettings();
        setting.setEnabled(Boolean.TRUE.equals(command.enabled()));
        setting.setBaseUrl(blankToEmpty(command.baseUrl()));
        setting.setModel(blankToEmpty(command.model()));
        if (command.apiKey() != null) {
            setting.setApiKey(command.apiKey().isBlank() ? "" : command.apiKey().trim());
        }
        setting.setUpdatedAt(LocalDateTime.now());
        llmSettingMapper.updateById(setting);
        return LlmSettingsView.from(setting);
    }

    private LlmSetting defaults() {
        LocalDateTime now = LocalDateTime.now();
        LlmSetting setting = new LlmSetting();
        setting.setId(SETTINGS_ID);
        setting.setEnabled(false);
        setting.setBaseUrl("");
        setting.setModel("");
        setting.setApiKey("");
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    public record UpdateLlmSettingsCommand(Boolean enabled, String baseUrl, String model, String apiKey) {
    }

    public record LlmSettingsView(Boolean enabled, String baseUrl, String model, Boolean apiKeySet) {
        static LlmSettingsView from(LlmSetting setting) {
            return new LlmSettingsView(
                    Boolean.TRUE.equals(setting.getEnabled()),
                    setting.getBaseUrl(),
                    setting.getModel(),
                    setting.getApiKey() != null && !setting.getApiKey().isBlank()
            );
        }
    }
}
