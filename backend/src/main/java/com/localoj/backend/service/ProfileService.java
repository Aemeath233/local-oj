package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ProfileService {
    private static final long MAX_AVATAR_BYTES = 2L * 1024L * 1024L;
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            MediaType.IMAGE_PNG_VALUE, ".png",
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final Path avatarRoot;

    public ProfileService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            @Value("${app.data-root:/data}") String dataRoot
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.avatarRoot = Paths.get(dataRoot).toAbsolutePath().normalize().resolve("avatars").normalize();
    }

    public ProfileView profile(CurrentUser currentUser) {
        return ProfileView.from(requireUser(currentUser.id()));
    }

    @Transactional
    public ProfileView updateProfile(CurrentUser currentUser, UpdateProfileCommand command) {
        User user = requireUser(currentUser.id());
        String studentNo = normalizeOptional(command.studentNo(), 64, "学号");
        if (studentNo != null) {
            Long duplicateCount = userMapper.selectCount(new QueryWrapper<User>()
                    .eq("student_no", studentNo)
                    .ne("id", user.getId()));
            if (duplicateCount > 0) {
                throw new IllegalArgumentException("学号已被使用");
            }
        }

        String displayName = normalizeRequired(command.displayName(), 128, "昵称");
        String major = normalizeOptional(command.major(), 128, "专业");
        LocalDateTime now = LocalDateTime.now();
        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", user.getId())
                .set("display_name", displayName)
                .set("student_no", studentNo)
                .set("major", major)
                .set("updated_at", now));
        user.setDisplayName(displayName);
        user.setStudentNo(studentNo);
        user.setMajor(major);
        user.setUpdatedAt(now);
        return ProfileView.from(user);
    }

    @Transactional
    public ProfileView updateAvatar(CurrentUser currentUser, MultipartFile file) {
        User user = requireUser(currentUser.id());
        String extension = avatarExtension(file);
        Path userDir = avatarRoot.resolve(String.valueOf(user.getId())).normalize();
        ensureInside(avatarRoot, userDir);
        String filename = "avatar-" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = userDir.resolve(filename).normalize();
        ensureInside(userDir, target);

        try {
            Files.createDirectories(userDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("保存头像失败", ex);
        }

        user.setAvatarUrl("/api/profile/avatar/" + user.getId() + "/" + filename);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return ProfileView.from(user);
    }

    public AvatarFile readAvatar(Long userId, String filename) {
        if (userId == null || filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("头像不存在");
        }
        Path userDir = avatarRoot.resolve(String.valueOf(userId)).normalize();
        Path path = userDir.resolve(filename).normalize();
        ensureInside(userDir, path);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("头像不存在");
        }
        try {
            return new AvatarFile(Files.readAllBytes(path), mediaTypeFor(filename));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取头像失败", ex);
        }
    }

    public void sendPasswordCode(CurrentUser currentUser) {
        User user = requireUser(currentUser.id());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("当前账号未绑定邮箱，无法通过 SMTP 修改密码");
        }
        emailVerificationService.sendPasswordChangeCode(user.getEmail());
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, ChangePasswordCommand command) {
        User user = requireUser(currentUser.id());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("当前账号未绑定邮箱，无法通过 SMTP 修改密码");
        }
        if (command.newPassword() == null || command.newPassword().length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位");
        }
        emailVerificationService.consumePasswordChangeCode(user.getEmail(), command.code());
        user.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private String avatarExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("头像不能超过 2MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (extension == null) {
            String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (filename.endsWith(".png")) {
                extension = ".png";
            } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                extension = ".jpg";
            } else if (filename.endsWith(".webp")) {
                extension = ".webp";
            } else if (filename.endsWith(".gif")) {
                extension = ".gif";
            }
        }
        if (extension == null) {
            throw new IllegalArgumentException("头像只支持 png、jpg、webp、gif");
        }
        return extension;
    }

    private String normalizeRequired(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "过长");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "过长");
        }
        return normalized;
    }

    private String mediaTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private static void ensureInside(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Invalid avatar path");
        }
    }

    public record UpdateProfileCommand(String displayName, String studentNo, String major) {
    }

    public record ChangePasswordCommand(String code, String newPassword) {
    }

    public record ProfileView(
            Long id,
            String username,
            String email,
            String displayName,
            String avatarUrl,
            String studentNo,
            String major,
            String role
    ) {
        static ProfileView from(User user) {
            return new ProfileView(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.getStudentNo(),
                    user.getMajor(),
                    user.getRole().name()
            );
        }
    }

    public record AvatarFile(byte[] bytes, String contentType) {
    }
}
