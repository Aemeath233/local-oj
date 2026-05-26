package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.common.enums.Role;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
import com.localoj.common.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
    private final UserMapper userMapper;
    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final PasswordEncoder passwordEncoder;
    private final Path dataRoot;

    public AdminDataService(
            UserMapper userMapper,
            SubmissionMapper submissionMapper,
            ProblemMapper problemMapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.data-root:/data}") String dataRootStr
    ) {
        this.userMapper = userMapper;
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.passwordEncoder = passwordEncoder;
        this.dataRoot = Paths.get(dataRootStr).toAbsolutePath().normalize();
    }

    // ================= 1. USER BULK IMPORT =================

    public static class ImportUserResult {
        public int total = 0;
        public int successCount = 0;
        public int failedCount = 0;
        public List<ImportedUserDetail> users = new ArrayList<>();
        public List<ImportErrorDetail> errors = new ArrayList<>();
    }

    public static class ImportedUserDetail {
        public String username;
        public String displayName;
        public String email;
        public String studentNo;
        public String password;
        public String role;
        public String status = "成功";

        public ImportedUserDetail(String username, String displayName, String email, String studentNo, String password, String role) {
            this.username = username;
            this.displayName = displayName;
            this.email = email;
            this.studentNo = studentNo;
            this.password = password;
            this.role = role;
        }
    }

    public static class ImportErrorDetail {
        public int row;
        public String username;
        public String reason;

        public ImportErrorDetail(int row, String username, String reason) {
            this.row = row;
            this.username = username;
            this.reason = reason;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ImportUserResult importUsers(MultipartFile file) throws IOException {
        ImportUserResult result = new ImportUserResult();
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                rows.add(parseCsvLine(line));
            }
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("未读取到任何有效数据");
        }

        result.total = rows.size();
        int startIndex = 0;
        List<String> firstRow = rows.get(0);

        // Detect headers
        Map<String, Integer> colMap = new HashMap<>();
        boolean hasHeader = false;
        for (int i = 0; i < firstRow.size(); i++) {
            String val = firstRow.get(i).trim().toLowerCase();
            if (val.contains("用户名") || val.contains("username")) {
                colMap.put("username", i);
                hasHeader = true;
            } else if (val.contains("邮箱") || val.contains("email")) {
                colMap.put("email", i);
                hasHeader = true;
            } else if (val.contains("昵称") || val.contains("display_name") || val.contains("displayname") || val.contains("姓名") || val.contains("name")) {
                colMap.put("displayName", i);
                hasHeader = true;
            } else if (val.contains("学号") || val.contains("student_no") || val.contains("studentno") || val.contains("student_number")) {
                colMap.put("studentNo", i);
                hasHeader = true;
            } else if (val.contains("专业") || val.contains("major")) {
                colMap.put("major", i);
                hasHeader = true;
            } else if (val.contains("密码") || val.contains("password")) {
                colMap.put("password", i);
                hasHeader = true;
            } else if (val.contains("角色") || val.contains("role")) {
                colMap.put("role", i);
                hasHeader = true;
            }
        }

        if (hasHeader) {
            startIndex = 1;
            result.total = rows.size() - 1;
        } else {
            // Default mapping if no header is found
            colMap.put("username", 0);
            colMap.put("email", 1);
            colMap.put("displayName", 2);
            colMap.put("studentNo", 3);
            colMap.put("major", 4);
            colMap.put("password", 5);
            colMap.put("role", 6);
        }

        // Set to prevent local import duplicate keys in memory
        Set<String> localUsernames = new HashSet<>();
        Set<String> localEmails = new HashSet<>();
        Set<String> localStudentNos = new HashSet<>();

        LocalDateTime now = LocalDateTime.now();

        for (int rowIndex = startIndex; rowIndex < rows.size(); rowIndex++) {
            int displayRowNo = rowIndex + 1;
            List<String> row = rows.get(rowIndex);

            // Fetch columns safely
            String username = getColVal(row, colMap.get("username"));
            String email = getColVal(row, colMap.get("email"));
            String displayName = getColVal(row, colMap.get("displayName"));
            String studentNo = getColVal(row, colMap.get("studentNo"));
            String major = getColVal(row, colMap.get("major"));
            String rawPassword = getColVal(row, colMap.get("password"));
            String roleStr = getColVal(row, colMap.get("role"));

            // 1. Validation: Username
            if (username == null || username.isBlank()) {
                result.errors.add(new ImportErrorDetail(displayRowNo, "", "用户名不能为空"));
                result.failedCount++;
                continue;
            }
            username = username.trim();
            if (!username.matches("[A-Za-z0-9_]{3,32}")) {
                result.errors.add(new ImportErrorDetail(displayRowNo, username, "用户名只能包含字母、数字、下划线，长度 3-32 位"));
                result.failedCount++;
                continue;
            }
            if (localUsernames.contains(username) || userMapper.selectCount(new QueryWrapper<User>().eq("username", username)) > 0) {
                result.errors.add(new ImportErrorDetail(displayRowNo, username, "用户名已被使用"));
                result.failedCount++;
                continue;
            }

            // 2. Validation: Email (Optional, must be saved as null if blank)
            if (email != null && !email.isBlank()) {
                email = email.trim();
                if (!email.contains("@")) {
                    result.errors.add(new ImportErrorDetail(displayRowNo, username, "邮箱格式不正确"));
                    result.failedCount++;
                    continue;
                }
                if (localEmails.contains(email) || userMapper.selectCount(new QueryWrapper<User>().eq("email", email)) > 0) {
                    result.errors.add(new ImportErrorDetail(displayRowNo, username, "邮箱已被使用"));
                    result.failedCount++;
                    continue;
                }
            } else {
                email = null;
            }

            // 3. Validation: Student No (Optional, must be saved as null if blank)
            if (studentNo != null && !studentNo.isBlank()) {
                studentNo = studentNo.trim();
                if (localStudentNos.contains(studentNo) || userMapper.selectCount(new QueryWrapper<User>().eq("student_no", studentNo)) > 0) {
                    result.errors.add(new ImportErrorDetail(displayRowNo, username, "学号已被使用"));
                    result.failedCount++;
                    continue;
                }
            } else {
                studentNo = null;
            }

            // 4. Password validation (At least 6 chars, auto generate if blank)
            String finalPassword;
            if (rawPassword == null || rawPassword.isBlank()) {
                finalPassword = "OJ@" + (100000 + new Random().nextInt(900000));
            } else {
                finalPassword = rawPassword.trim();
                if (finalPassword.length() < 6) {
                    result.errors.add(new ImportErrorDetail(displayRowNo, username, "密码长度必须至少为 6 位"));
                    result.failedCount++;
                    continue;
                }
            }

            // 5. Role parsing
            Role finalRole = Role.STUDENT;
            if (roleStr != null && !roleStr.isBlank()) {
                String roleLower = roleStr.trim().toLowerCase();
                if (roleLower.equals("super_admin") || roleLower.equals("superadmin") || roleLower.contains("超级管理员")) {
                    finalRole = Role.SUPER_ADMIN;
                } else if (roleLower.equals("admin") || roleLower.contains("管理员")) {
                    finalRole = Role.ADMIN;
                } else if (roleLower.equals("student") || roleLower.contains("学生") || roleLower.contains("用户")) {
                    finalRole = Role.STUDENT;
                }
            }

            // Save to memory lists to prevent local file duplicates
            localUsernames.add(username);
            if (email != null) localEmails.add(email);
            if (studentNo != null) localStudentNos.add(studentNo);

            // 6. Insertion
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName.trim());
            user.setStudentNo(studentNo);
            user.setMajor(major == null || major.isBlank() ? null : major.trim());
            user.setPasswordHash(passwordEncoder.encode(finalPassword));
            user.setRole(finalRole);
            user.setEnabled(true);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);

            userMapper.insert(user);

            result.users.add(new ImportedUserDetail(username, user.getDisplayName(), email, studentNo, finalPassword, finalRole.name()));
            result.successCount++;
        }

        return result;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        if (line == null) {
            return result;
        }
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString());
        return result;
    }

    private String getColVal(List<String> row, Integer colIndex) {
        if (colIndex == null || colIndex < 0 || colIndex >= row.size()) {
            return null;
        }
        String val = row.get(colIndex);
        if (val == null) {
            return null;
        }
        // Remove surrounding double quotes if present
        val = val.trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1);
        }
        return val.trim();
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
        public String host = "mysql";
        public int port = 3306;
        public String dockerBackupCommand;
        public String dockerRestoreCommand;
        public String nativeBackupCommand;
        public String nativeRestoreCommand;
        public List<String> recommendations = new ArrayList<>();
    }

    public BackupInfo getBackupInfo(String dbUrl, String username) {
        BackupInfo info = new BackupInfo();
        info.username = username;

        // Parse DB Url e.g. jdbc:mysql://mysql:3306/local_oj?...
        String dbName = "local_oj";
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

        String passwordEnvName = "root".equalsIgnoreCase(username) ? "MYSQL_ROOT_PASSWORD" : "MYSQL_PASSWORD";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        // Docker environment backup (runs on host machine, targeting localoj-mysql container)
        info.dockerBackupCommand = String.format("docker exec localoj-mysql sh -c 'MYSQL_PWD=\"$%s\" mysqldump -u%s %s' > backup_%s.sql",
                passwordEnvName, username, dbName, timestamp);
        info.dockerRestoreCommand = String.format("docker exec -i localoj-mysql sh -c 'MYSQL_PWD=\"$%s\" mysql -u%s %s' < backup.sql",
                passwordEnvName, username, dbName);

        // Native exposed port environment backup (runs on host machine, targeting exposed port 3307)
        info.nativeBackupCommand = String.format("mysqldump -h 127.0.0.1 -P 3307 -u%s -p %s > backup_%s.sql",
                username, dbName, timestamp);
        info.nativeRestoreCommand = String.format("mysql -h 127.0.0.1 -P 3307 -u%s -p %s < backup.sql",
                username, dbName);

        // Add helpful notes
        info.recommendations.add("备份与恢复为核心高危操作，请在业务空闲期（非提交/评测活跃期）执行，避免锁表导致评测任务堆积。");
        info.recommendations.add("若使用 Docker-Compose 部署，执行宿主机备份命令前，请确保 `localoj-mysql` 容器正在运行且网络畅通。");
        info.recommendations.add("页面不会展示数据库明文密码。Docker 命令会读取 MySQL 容器内的密码环境变量；Native 命令会在终端交互式提示输入密码。");
        info.recommendations.add("OJ 系统的完整备份不仅包含 SQL 数据库，还应包含题目的物理测试数据（数据存放在主机卷或 `/data/problems`）。");
        info.recommendations.add("恢复数据库备份文件时，如果含有 `CREATE TABLE`，请确保数据库版本的一致性（本系统内置 MySQL 8.4）。");

        return info;
    }
}
