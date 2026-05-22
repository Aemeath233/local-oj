package com.localoj.backend.service;

import com.localoj.common.mapper.SmtpSettingMapper;
import com.localoj.common.model.SmtpSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SmtpSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final SmtpSettingMapper smtpSettingMapper;

    public SmtpSettingsService(SmtpSettingMapper smtpSettingMapper) {
        this.smtpSettingMapper = smtpSettingMapper;
    }

    public SmtpSetting requireSettings() {
        SmtpSetting setting = smtpSettingMapper.selectById(SETTINGS_ID);
        if (setting != null) {
            return setting;
        }
        SmtpSetting created = defaults();
        smtpSettingMapper.insert(created);
        return created;
    }

    public SmtpSettingsView view() {
        return SmtpSettingsView.from(requireSettings());
    }

    @Transactional
    public SmtpSettingsView update(UpdateSmtpSettingsCommand command) {
        SmtpSetting setting = requireSettings();
        setting.setEnabled(Boolean.TRUE.equals(command.enabled()));
        setting.setHost(blankToEmpty(command.host()));
        setting.setPort(command.port() == null ? 587 : command.port());
        setting.setUsername(blankToEmpty(command.username()));
        setting.setFromAddress(blankToEmpty(command.fromAddress()));
        setting.setFromName(blankToEmpty(command.fromName()));
        setting.setAuthEnabled(command.authEnabled() == null || command.authEnabled());
        setting.setUseSsl(Boolean.TRUE.equals(command.useSsl()));
        setting.setUseStarttls(command.useStarttls() == null || command.useStarttls());
        if (command.password() != null) {
            setting.setPassword(command.password().isBlank() ? "" : command.password());
        }
        setting.setUpdatedAt(LocalDateTime.now());
        smtpSettingMapper.updateById(setting);
        return SmtpSettingsView.from(setting);
    }

    private SmtpSetting defaults() {
        LocalDateTime now = LocalDateTime.now();
        SmtpSetting setting = new SmtpSetting();
        setting.setId(SETTINGS_ID);
        setting.setEnabled(false);
        setting.setPort(587);
        setting.setAuthEnabled(true);
        setting.setUseSsl(false);
        setting.setUseStarttls(true);
        setting.setCreatedAt(now);
        setting.setUpdatedAt(now);
        return setting;
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    public record UpdateSmtpSettingsCommand(
            Boolean enabled,
            String host,
            Integer port,
            String username,
            String password,
            String fromAddress,
            String fromName,
            Boolean authEnabled,
            Boolean useSsl,
            Boolean useStarttls
    ) {
    }

    public record SmtpSettingsView(
            Boolean enabled,
            String host,
            Integer port,
            String username,
            String fromAddress,
            String fromName,
            Boolean authEnabled,
            Boolean useSsl,
            Boolean useStarttls,
            Boolean passwordSet
    ) {
        static SmtpSettingsView from(SmtpSetting setting) {
            return new SmtpSettingsView(
                    Boolean.TRUE.equals(setting.getEnabled()),
                    setting.getHost(),
                    setting.getPort(),
                    setting.getUsername(),
                    setting.getFromAddress(),
                    setting.getFromName(),
                    Boolean.TRUE.equals(setting.getAuthEnabled()),
                    Boolean.TRUE.equals(setting.getUseSsl()),
                    Boolean.TRUE.equals(setting.getUseStarttls()),
                    setting.getPassword() != null && !setting.getPassword().isBlank()
            );
        }
    }
}
