package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.Submission;
import com.coderushoj.common.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AdminDataService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminDataService.class);
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final PasswordEncoder passwordEncoder;
    private final TestCaseFileStorage testCaseFileStorage;
    private final Path dataRoot;

    public AdminDataService(
            UserMapper userMapper,
            SubmissionMapper submissionMapper,
            ProblemMapper problemMapper,
            PasswordEncoder passwordEncoder,
            TestCaseFileStorage testCaseFileStorage,
            @Value("${app.data-root:/data}") String dataRootStr
    ) {
        this.userMapper = userMapper;
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.passwordEncoder = passwordEncoder;
        this.testCaseFileStorage = testCaseFileStorage;
        this.dataRoot = Paths.get(dataRootStr).toAbsolutePath().normalize();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledCleanExpiredUploads() {
        try {
            log.info("Running scheduled cleanup for expired test cases uploads...");
            testCaseFileStorage.cleanExpiredUploads();
        } catch (Exception e) {
            log.error("Scheduled uploads cleanup failed", e);
        }
    }



    // ================= 2. SUBMISSION CLEANUP =================

    public static class CleanupRequest {
        public String confirmationPhrase;
        public String beforeDate; // LocalDateTime string or null
        public Long problemId;
        public Long userId;
        public Long contestId;
        public Boolean onlyPractice;
        public Boolean onlyContest;
        public List<String> verdicts;
    }

    @Transactional(rollbackFor = Exception.class)
    public int cleanupSubmissions(CleanupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("清理请求不能为空");
        }
        if (!"FORCE CLEANUP".equals(request.confirmationPhrase == null ? "" : request.confirmationPhrase.trim())) {
            throw new IllegalArgumentException("清理确认短语不正确");
        }
        if (Boolean.TRUE.equals(request.onlyPractice) && Boolean.TRUE.equals(request.onlyContest)) {
            throw new IllegalArgumentException("清理范围不能同时选择练习和比赛");
        }
        if (request.contestId != null && Boolean.TRUE.equals(request.onlyPractice)) {
            throw new IllegalArgumentException("指定比赛时不能同时选择仅练习提交");
        }
        if (!hasCleanupFilter(request)) {
            throw new IllegalArgumentException("请至少指定一个清理条件，禁止无条件清空全部提交记录");
        }

        QueryWrapper<Submission> wrapper = new QueryWrapper<>();

        if (request.beforeDate != null && !request.beforeDate.isBlank()) {
            LocalDateTime dt = LocalDateTime.parse(request.beforeDate, DateTimeFormatter.ISO_DATE_TIME);
            wrapper.lt("created_at", dt);
        }

        if (request.problemId != null) {
            wrapper.eq("problem_id", request.problemId);
        }

        if (request.userId != null) {
            wrapper.eq("user_id", request.userId);
        }

        if (request.contestId != null) {
            wrapper.eq("contest_id", request.contestId);
        }

        if (Boolean.TRUE.equals(request.onlyPractice)) {
            wrapper.isNull("contest_id");
        } else if (Boolean.TRUE.equals(request.onlyContest)) {
            wrapper.isNotNull("contest_id");
        }

        if (request.verdicts != null && !request.verdicts.isEmpty()) {
            wrapper.in("verdict", request.verdicts);
        }

        // Running delete cascades to submission_case_results automatically by DB FK constraints
        return submissionMapper.delete(wrapper);
    }

    private boolean hasCleanupFilter(CleanupRequest request) {
        return (request.beforeDate != null && !request.beforeDate.isBlank())
                || request.problemId != null
                || request.userId != null
                || request.contestId != null
                || Boolean.TRUE.equals(request.onlyPractice)
                || Boolean.TRUE.equals(request.onlyContest)
                || (request.verdicts != null && !request.verdicts.isEmpty());
    }

    // ================= 3. STORAGE & ORPHANED CASES STATS =================

    public static class StorageStats {
        public long totalSpaceBytes = 0;
        public long freeSpaceBytes = 0;
        public List<ProblemStorageStat> problemStats = new ArrayList<>();
    }

    public static class ProblemStorageStat {
        public Long problemId;
        public String title;
        public String slug;
        public int fileCount = 0;
        public long totalSizeBytes = 0;
        public boolean orphaned = false;
    }

    public StorageStats getStorageStats() throws IOException {
        StorageStats stats = new StorageStats();

        // Get free disk space
        if (Files.exists(dataRoot)) {
            stats.freeSpaceBytes = Files.getFileStore(dataRoot).getUsableSpace();
        } else {
            stats.freeSpaceBytes = 0;
        }

        Path problemsDir = dataRoot.resolve("problems").normalize();
        if (!Files.exists(problemsDir)) {
            return stats;
        }

        // Get all active problems in database
        List<Problem> activeProblems = problemMapper.selectList(new QueryWrapper<>());
        Map<Long, Problem> activeProblemsMap = new HashMap<>();
        for (Problem p : activeProblems) {
            activeProblemsMap.put(p.getId(), p);
        }

        // List directories in /data/problems/
        try (var stream = Files.list(problemsDir)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                String dirName = path.getFileName().toString();
                Long problemId = null;
                try {
                    problemId = Long.parseLong(dirName);
                } catch (NumberFormatException ignored) {}

                if (problemId != null) {
                    ProblemStorageStat stat = new ProblemStorageStat();
                    stat.problemId = problemId;

                    Problem dbProblem = activeProblemsMap.get(problemId);
                    if (dbProblem != null) {
                        stat.title = dbProblem.getTitle();
                        stat.slug = dbProblem.getSlug();
                        stat.orphaned = false;
                    } else {
                        stat.title = "已删除题目 (ID: " + problemId + ")";
                        stat.slug = "n/a";
                        stat.orphaned = true;
                    }

                    // Compute files and size in caseDir
                    Path casesPath = path.resolve("cases").normalize();
                    if (Files.exists(casesPath) && Files.isDirectory(casesPath)) {
                        computeFolderSizeAndCount(casesPath, stat);
                    }

                    stats.problemStats.add(stat);
                    stats.totalSpaceBytes += stat.totalSizeBytes;
                }
            });
        }

        // Sort by total space descending
        stats.problemStats.sort((a, b) -> Long.compare(b.totalSizeBytes, a.totalSizeBytes));

        return stats;
    }

    private void computeFolderSizeAndCount(Path folder, ProblemStorageStat stat) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    stat.fileCount++;
                    stat.totalSizeBytes += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    public int cleanOrphanedDirectories() throws IOException {
        Path problemsDir = dataRoot.resolve("problems").normalize();
        if (!Files.exists(problemsDir)) {
            return 0;
        }

        List<Problem> activeProblems = problemMapper.selectList(new QueryWrapper<>());
        Set<String> activeIds = new HashSet<>();
        for (Problem p : activeProblems) {
            activeIds.add(String.valueOf(p.getId()));
        }

        final int[] cleanedCount = {0};
        try (var stream = Files.list(problemsDir)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                String dirName = path.getFileName().toString();
                if (dirName.matches("\\d+") && !activeIds.contains(dirName)) {
                    deleteFolderRecursively(path);
                    cleanedCount[0]++;
                }
            });
        }

        return cleanedCount[0];
    }

    private void deleteFolderRecursively(Path folder) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    // ================= 4. BACKUP INFO & COMMAND GENERATOR =================

    public static class BackupInfo {
        public String database;
        public String username;
        public String host = "localhost";
        public int port = 3306;
        public String localBackupCommand;
        public String localRestoreCommand;
        public String remoteBackupCommand;
        public String remoteRestoreCommand;
        public List<String> recommendations = new ArrayList<>();
    }

    public BackupInfo getBackupInfo(String dbUrl, String username, String mysqlPortMapping) {
        BackupInfo info = new BackupInfo();
        info.username = username;

        // Parse DB Url e.g. jdbc:mysql://localhost:3306/coderush_oj?...
        String dbName = "coderush_oj";
        try {
            String cleanUrl = dbUrl.substring(dbUrl.indexOf("//") + 2);
            int slashIdx = cleanUrl.indexOf('/');
            int questionIdx = cleanUrl.indexOf('?');
            if (questionIdx != -1) {
                dbName = cleanUrl.substring(slashIdx + 1, questionIdx);
            } else {
                dbName = cleanUrl.substring(slashIdx + 1);
            }

            String hostAndPort = cleanUrl.substring(0, slashIdx);
            if (hostAndPort.contains(":")) {
                String[] parts = hostAndPort.split(":");
                info.host = parts[0];
                info.port = Integer.parseInt(parts[1]);
            } else {
                info.host = hostAndPort;
                info.port = 3306;
            }
        } catch (Exception ignored) {}

        info.database = dbName;

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Local CLI environment backup (runs on the local server without host/port options)
        info.localBackupCommand = String.format("mysqldump -u%s -p %s > backup_%s.sql",
                username, dbName, timestamp);
        info.localRestoreCommand = String.format("mysql -u%s -p %s < backup.sql",
                username, dbName);

        // Remote/TCP environment backup (explicitly specifies host and port)
        info.remoteBackupCommand = String.format("mysqldump -h %s -P %d -u%s -p %s > backup_%s.sql",
                info.host, info.port, username, dbName, timestamp);
        info.remoteRestoreCommand = String.format("mysql -h %s -P %d -u%s -p %s < backup.sql",
                info.host, info.port, username, dbName);

        // Add helpful notes
        info.recommendations.add("备份与恢复为核心高危操作，请在业务空闲期（非提交/评测活跃期）执行，避免锁表导致评测任务堆积。");
        info.recommendations.add("执行备份与恢复命令前，请确保您的系统环境变量中已正确安装并配置了 `mysql` 和 `mysqldump` 命令行客户端。");
        info.recommendations.add("安全提示：本页面不展示数据库明文密码。在命令行终端运行命令后，系统会交互式提示您输入密码，请配合输入数据库管理员密码。");
        info.recommendations.add("OJ 系统的完整备份不仅包含数据库 SQL，还应包含题目的物理测试数据（即您在配置中指定的本地数据存储根目录下的 `data/oj` 等物理数据）。");
        info.recommendations.add("恢复数据库备份文件时，如果 SQL 文件中含有建表语句 `CREATE TABLE`，请确保恢复前后的 MySQL 版本具有较好的兼容性（本地运行推荐使用 MySQL 8.0+）。");

        return info;
    }

    private NativeEndpoint parseNativeEndpoint(String mysqlPortMapping) {
        String mapping = mysqlPortMapping == null || mysqlPortMapping.isBlank()
                ? "127.0.0.1:3307"
                : mysqlPortMapping.trim();
        String host = "127.0.0.1";
        int port = 3307;
        try {
            if (mapping.contains(":")) {
                int idx = mapping.lastIndexOf(':');
                host = mapping.substring(0, idx);
                port = Integer.parseInt(mapping.substring(idx + 1));
                if ("0.0.0.0".equals(host) || "::".equals(host)) {
                    host = "127.0.0.1";
                }
            } else {
                port = Integer.parseInt(mapping);
            }
        } catch (RuntimeException ignored) {
            host = "127.0.0.1";
            port = 3307;
        }
        return new NativeEndpoint(host, port);
    }

    private record NativeEndpoint(String host, int port) {
    }
}
