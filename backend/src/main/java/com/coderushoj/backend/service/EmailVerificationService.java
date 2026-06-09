package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.mapper.EmailVerificationCodeMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.EmailVerificationCode;
import com.coderushoj.common.model.SmtpSetting;
import com.coderushoj.common.model.User;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Properties;

@Service
public class EmailVerificationService {
    private static final String REGISTER_PURPOSE = "REGISTER";
    private static final String PASSWORD_CHANGE_PURPOSE = "PASSWORD_CHANGE";
    private static final String EMAIL_CHANGE_PURPOSE = "EMAIL_CHANGE";
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationCodeMapper codeMapper;
    private final UserMapper userMapper;
    private final SmtpSettingsService smtpSettingsService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public EmailVerificationService(
            EmailVerificationCodeMapper codeMapper,
            UserMapper userMapper,
            SmtpSettingsService smtpSettingsService,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate
    ) {
        this.codeMapper = codeMapper;
        this.userMapper = userMapper;
        this.smtpSettingsService = smtpSettingsService;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void sendRegisterCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        ensureEmailNotUsed(normalizedEmail);
        sendCode(normalizedEmail, REGISTER_PURPOSE, "Local Judge 注册验证码", "你的 Local Judge 注册验证码是：");
    }

    @Transactional
    public void sendPasswordChangeCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        sendCode(normalizedEmail, PASSWORD_CHANGE_PURPOSE, "Local Judge 修改密码验证码", "你的 Local Judge 修改密码验证码是：");
    }

    public void applyPasswordResetCooldown(String email) {
        String normalizedEmail = normalizeEmail(email);
        applySendCooldown(normalizedEmail, PASSWORD_CHANGE_PURPOSE);
    }

    private void sendCode(String normalizedEmail, String purpose, String subject, String textPrefix) {
        String cooldownKey = applySendCooldown(normalizedEmail, purpose);

        try {
            SmtpSetting setting = smtpSettingsService.requireSettings();
            validateSmtpSetting(setting);

            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            LocalDateTime now = LocalDateTime.now();
            codeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EmailVerificationCode>()
                    .eq("email", normalizedEmail)
                    .eq("purpose", purpose)
                    .eq("consumed", false)
                    .set("consumed", true));

            EmailVerificationCode verificationCode = new EmailVerificationCode();
            verificationCode.setEmail(normalizedEmail);
            verificationCode.setCode(code);
            verificationCode.setPurpose(purpose);
            verificationCode.setExpiresAt(now.plusMinutes(10));
            verificationCode.setConsumed(false);
            verificationCode.setCreatedAt(now);
            codeMapper.insert(verificationCode);
            redisTemplate.delete(attemptKey(normalizedEmail, purpose));

            sendMail(setting, normalizedEmail, code, subject, textPrefix);
        } catch (Exception ex) {
            redisTemplate.delete(cooldownKey);
            throw ex;
        }
    }

    @Transactional
    public void consumeRegisterCode(String email, String code) {
        consumeCode(email, code, REGISTER_PURPOSE);
    }

    @Transactional
    public void consumePasswordChangeCode(String email, String code) {
        consumeCode(email, code, PASSWORD_CHANGE_PURPOSE);
    }

    @Transactional
    public void sendEmailChangeCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        sendCode(normalizedEmail, EMAIL_CHANGE_PURPOSE, "Local Judge 换绑邮箱验证码", "你的 Local Judge 换绑邮箱验证码是：");
    }

    @Transactional
    public void consumeEmailChangeCode(String email, String code) {
        consumeCode(email, code, EMAIL_CHANGE_PURPOSE);
    }

    private void consumeCode(String email, String code, String purpose) {
        String normalizedEmail = normalizeEmail(email);
        ensureAttemptsAllowed(normalizedEmail, purpose);
        EmailVerificationCode verificationCode = codeMapper.selectOne(new QueryWrapper<EmailVerificationCode>()
                .eq("email", normalizedEmail)
                .eq("purpose", purpose)
                .eq("consumed", false)
                .ge("expires_at", LocalDateTime.now())
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (verificationCode == null || !verificationCode.getCode().equals(code)) {
            recordFailedAttempt(normalizedEmail, purpose);
            throw new IllegalArgumentException("验证码不正确或已过期");
        }
        verificationCode.setConsumed(true);
        codeMapper.updateById(verificationCode);
        redisTemplate.delete(attemptKey(normalizedEmail, purpose));
    }

    private String applySendCooldown(String normalizedEmail, String purpose) {
        String cooldownKey = "cooldown:email:code:" + purpose + ":" + normalizedEmail;
        Boolean cooldownSuccess = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "1", java.time.Duration.ofSeconds(60));
        if (cooldownSuccess == null || !cooldownSuccess) {
            throw new IllegalArgumentException("验证码发送过于频繁，请一分钟后再试！");
        }
        return cooldownKey;
    }

    private void ensureAttemptsAllowed(String normalizedEmail, String purpose) {
        String attempts = redisTemplate.opsForValue().get(attemptKey(normalizedEmail, purpose));
        if (attempts != null) {
            try {
                if (Integer.parseInt(attempts) >= MAX_CODE_ATTEMPTS) {
                    throw new IllegalArgumentException("验证码错误次数过多，请重新获取验证码");
                }
            } catch (NumberFormatException ignored) {
                redisTemplate.delete(attemptKey(normalizedEmail, purpose));
            }
        }
    }

    private void recordFailedAttempt(String normalizedEmail, String purpose) {
        String key = attemptKey(normalizedEmail, purpose);
        Long attempts = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, java.time.Duration.ofMinutes(10));
        if (attempts != null && attempts >= MAX_CODE_ATTEMPTS) {
            codeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<EmailVerificationCode>()
                    .eq("email", normalizedEmail)
                    .eq("purpose", purpose)
                    .eq("consumed", false)
                    .set("consumed", true));
            throw new IllegalArgumentException("验证码错误次数过多，请重新获取验证码");
        }
    }

    private String attemptKey(String normalizedEmail, String purpose) {
        return "attempt:email:code:" + purpose + ":" + normalizedEmail;
    }

    public String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return email.trim().toLowerCase();
    }

    private void ensureEmailNotUsed(String email) {
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("email", email));
        if (count > 0) {
            throw new IllegalArgumentException("邮箱已被注册");
        }
    }

    private void validateSmtpSetting(SmtpSetting setting) {
        if (!Boolean.TRUE.equals(setting.getEnabled())) {
            throw new IllegalArgumentException("SMTP 尚未启用");
        }
        if (setting.getHost() == null || setting.getHost().isBlank()) {
            throw new IllegalArgumentException("SMTP 主机未配置");
        }
        if (setting.getFromAddress() == null || setting.getFromAddress().isBlank()) {
            throw new IllegalArgumentException("发件邮箱未配置");
        }
        if (Boolean.TRUE.equals(setting.getAuthEnabled())
                && (setting.getUsername() == null || setting.getUsername().isBlank())) {
            throw new IllegalArgumentException("SMTP 用户名未配置");
        }
    }

    private void sendMail(SmtpSetting setting, String email, String code, String subject, String textPrefix) {
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(setting.getHost());
            sender.setPort(setting.getPort() == null ? 587 : setting.getPort());
            if (Boolean.TRUE.equals(setting.getAuthEnabled())) {
                sender.setUsername(setting.getUsername());
                sender.setPassword(setting.getPassword());
            }
            sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

            Properties properties = sender.getJavaMailProperties();
            properties.put("mail.smtp.auth", String.valueOf(Boolean.TRUE.equals(setting.getAuthEnabled())));
            properties.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(setting.getUseSsl())));
            properties.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(setting.getUseStarttls())));
            properties.put("mail.smtp.connectiontimeout", "10000");
            properties.put("mail.smtp.timeout", "10000");
            properties.put("mail.smtp.writetimeout", "10000");

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(setting.getFromAddress(), defaultFromName(setting)));
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(textPrefix + code + "\n\n验证码 10 分钟内有效。");
            sender.send(message);
        } catch (Exception ex) {
            throw new IllegalArgumentException("邮件发送失败，请检查 SMTP 配置");
        }
    }

    private String defaultFromName(SmtpSetting setting) {
        return setting.getFromName() == null || setting.getFromName().isBlank() ? "Local Judge" : setting.getFromName();
    }
}
