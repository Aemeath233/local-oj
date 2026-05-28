package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.model.User;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
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
import java.util.Set;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final Path avatarRoot;

    public ProfileService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            ProblemMapper problemMapper,
            SubmissionMapper submissionMapper,
            @Value("${app.data-root:/data}") String dataRoot
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.avatarRoot = Paths.get(dataRoot).toAbsolutePath().normalize().resolve("avatars").normalize();
    }

    public ProfileView profile(CurrentUser currentUser) {
        return ProfileView.from(requireUser(currentUser.id()));
    }

    @Transactional
    public ProfileView updateProfile(CurrentUser currentUser, UpdateProfileCommand command) {
        User user = requireUser(currentUser.id());
        String studentNo = normalizeOptional(command.studentNo(), 64, "班级");
        String displayName = normalizeRequired(command.displayName(), 128, "昵称");
        String major = normalizeOptional(command.major(), 128, "专业");

        String newUsername = command.username() == null ? "" : command.username().trim();
        boolean usernameChanged = false;

        if (!newUsername.isBlank() && !newUsername.equals(user.getUsername())) {
            if (!newUsername.matches("[A-Za-z0-9_]{3,32}")) {
                throw new IllegalArgumentException("用户名只能包含字母、数字、下划线，长度 3-32 位");
            }

            Long duplicateCount = userMapper.selectCount(new QueryWrapper<User>()
                    .eq("username", newUsername)
                    .ne("id", user.getId()));
            if (duplicateCount > 0) {
                throw new IllegalArgumentException("用户名已被使用");
            }

            LocalDateTime now = LocalDateTime.now();
            int currentMonthCount = user.getUsernameChangeCountCurrentMonth() == null ? 0 : user.getUsernameChangeCountCurrentMonth();
            if (user.getLastUsernameChangedAt() != null) {
                LocalDateTime lastChanged = user.getLastUsernameChangedAt();
                if (lastChanged.getYear() != now.getYear() || lastChanged.getMonthValue() != now.getMonthValue()) {
                    currentMonthCount = 0;
                }
            }

            if (currentMonthCount >= 3) {
                throw new IllegalArgumentException("每个月最多只能修改 3 次用户名！");
            }

            user.setUsername(newUsername);
            user.setUsernameChangeCountCurrentMonth(currentMonthCount + 1);
            user.setLastUsernameChangedAt(now);
            usernameChanged = true;
        }

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<User>()
                .eq("id", user.getId())
                .set("display_name", displayName)
                .set("student_no", studentNo)
                .set("major", major)
                .set("updated_at", now);

        if (usernameChanged) {
            updateWrapper.set("username", user.getUsername())
                    .set("username_change_count_current_month", user.getUsernameChangeCountCurrentMonth())
                    .set("last_username_changed_at", user.getLastUsernameChangedAt());
        }

        userMapper.update(null, updateWrapper);

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

    public record UpdateProfileCommand(String username, String displayName, String studentNo, String major) {
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
            String role,
            Integer usernameChangeCountCurrentMonth,
            LocalDateTime lastUsernameChangedAt
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
                    user.getRole().name(),
                    user.getUsernameChangeCountCurrentMonth(),
                    user.getLastUsernameChangedAt()
            );
        }
    }

    public record PublicProfileView(
            Long id,
            String username,
            String displayName,
            String avatarUrl,
            String major,
            String role,
            UserStatsView stats
    ) {}

    public PublicProfileView getPublicProfile(Long userId) {
        User user = requireUser(userId);
        UserStatsView stats = getUserStatsById(userId);
        return new PublicProfileView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getMajor(),
                user.getRole().name(),
                stats
        );
    }

    public void sendEmailChangeCode(CurrentUser currentUser) {
        User user = requireUser(currentUser.id());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("当前账号未绑定邮箱，无法发送换绑验证码");
        }
        emailVerificationService.sendEmailChangeCode(user.getEmail());
    }

    @Transactional
    public ProfileView changeEmail(CurrentUser currentUser, String newEmail, String code) {
        User user = requireUser(currentUser.id());
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException("当前账号没有邮箱，无法换绑");
        }
        String normalizedNewEmail = emailVerificationService.normalizeEmail(newEmail);
        if (normalizedNewEmail.equals(user.getEmail())) {
            throw new IllegalArgumentException("新邮箱不能与旧邮箱相同");
        }
        emailVerificationService.consumeEmailChangeCode(user.getEmail(), code);

        Long count = userMapper.selectCount(new QueryWrapper<User>()
                .eq("email", normalizedNewEmail)
                .ne("id", user.getId()));
        if (count > 0) {
            throw new IllegalArgumentException("新邮箱已被其他账号绑定");
        }

        user.setEmail(normalizedNewEmail);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        return ProfileView.from(user);
    }

    public record AvatarFile(byte[] bytes, String contentType) {
    }

    public record UserStatsView(
            Map<String, SubmissionDailyStats> heatmap,
            DifficultyDistribution difficultyDistribution
    ) {}

    public record SubmissionDailyStats(
            int totalCount,
            int acCount
    ) {}

    public record DifficultyDistribution(
            int easySolved,
            int easyTotal,
            int mediumSolved,
            int mediumTotal,
            int hardSolved,
            int hardTotal
    ) {}

    public UserStatsView getUserStats(CurrentUser currentUser) {
        return getUserStatsById(currentUser.id());
    }

    public UserStatsView getUserStatsById(Long userId) {
        // 1. Get all visible problems to count totals by difficulty
        List<Problem> visibleProblems = problemMapper.selectList(new QueryWrapper<Problem>().eq("visible", true));
        int easyTotal = 0;
        int mediumTotal = 0;
        int hardTotal = 0;
        Map<Long, String> problemIdToDifficulty = new HashMap<>();

        for (Problem p : visibleProblems) {
            String diff = p.getDifficulty() == null ? "Easy" : p.getDifficulty();
            problemIdToDifficulty.put(p.getId(), diff);
            if ("Easy".equalsIgnoreCase(diff)) {
                easyTotal++;
            } else if ("Medium".equalsIgnoreCase(diff)) {
                mediumTotal++;
            } else if ("Hard".equalsIgnoreCase(diff)) {
                hardTotal++;
            }
        }

        // 2. Count user's unique solved problems (verdict = AC)
        List<Submission> acSubmissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .eq("user_id", userId)
                .eq("verdict", com.localoj.common.enums.Verdict.AC));
        Set<Long> solvedProblemIds = acSubmissions.stream()
                .map(Submission::getProblemId)
                .collect(Collectors.toSet());

        int easySolved = 0;
        int mediumSolved = 0;
        int hardSolved = 0;
        for (Long pid : solvedProblemIds) {
            String diff = problemIdToDifficulty.get(pid);
            if (diff != null) {
                if ("Easy".equalsIgnoreCase(diff)) {
                    easySolved++;
                } else if ("Medium".equalsIgnoreCase(diff)) {
                    mediumSolved++;
                } else if ("Hard".equalsIgnoreCase(diff)) {
                    hardSolved++;
                }
            }
        }

        DifficultyDistribution difficultyDistribution = new DifficultyDistribution(
                easySolved, easyTotal,
                mediumSolved, mediumTotal,
                hardSolved, hardTotal
        );

        // 3. Get submissions in the last 365 days for the heatmap
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        List<Submission> recentSubmissions = submissionMapper.selectList(new QueryWrapper<Submission>()
                .eq("user_id", userId)
                .ge("created_at", oneYearAgo));

        Map<String, SubmissionDailyStats> heatmap = new HashMap<>();
        for (Submission s : recentSubmissions) {
            if (s.getCreatedAt() == null) continue;
            String dateStr = s.getCreatedAt().toLocalDate().toString(); // "yyyy-MM-dd"
            SubmissionDailyStats stats = heatmap.getOrDefault(dateStr, new SubmissionDailyStats(0, 0));
            int total = stats.totalCount() + 1;
            int ac = stats.acCount() + (s.getVerdict() == com.localoj.common.enums.Verdict.AC ? 1 : 0);
            heatmap.put(dateStr, new SubmissionDailyStats(total, ac));
        }

        return new UserStatsView(heatmap, difficultyDistribution);
    }
}
