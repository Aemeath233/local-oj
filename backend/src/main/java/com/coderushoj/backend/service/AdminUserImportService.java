package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminUserImportService {
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserImportService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

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

        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        List<List<String>> rows = parseCsv(content);

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
            } else if (val.contains("学号") || val.contains("班级") || val.contains("class") || val.contains("student_no") || val.contains("studentno") || val.contains("student_number")) {
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

        Set<String> localUsernames = new HashSet<>();
        Set<String> localEmails = new HashSet<>();

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
            } else {
                studentNo = null;
            }

            // 4. Password validation (At least 6 chars, auto generate if blank)
            String finalPassword;
            if (rawPassword == null || rawPassword.isBlank()) {
                finalPassword = "OJ@" + (100000 + PASSWORD_RANDOM.nextInt(900000));
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

    private List<List<String>> parseCsv(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        int len = content.length();
        for (int i = 0; i < len; i++) {
            char c = content.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < len && content.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                currentRow.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r') {
                if (inQuotes) {
                    cell.append(c);
                }
            } else if (c == '\n' && !inQuotes) {
                currentRow.add(cell.toString());
                cell.setLength(0);
                if (!currentRow.stream().allMatch(String::isBlank)) {
                    rows.add(new ArrayList<>(currentRow));
                }
                currentRow.clear();
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(cell.toString());
            if (!currentRow.stream().allMatch(String::isBlank)) {
                rows.add(currentRow);
            }
        }
        return rows;
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
}
