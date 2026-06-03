package com.localoj.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.common.enums.Role;
import com.localoj.common.mapper.UserMapper;
import com.localoj.common.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<List<AdminUserView>> list(@RequestParam(value = "search", required = false) String search) {
        QueryWrapper<User> wrapper = new QueryWrapper<User>().orderByDesc("id");
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim() + "%";
            wrapper.and(w -> w.like("username", term)
                    .or().like("email", term)
                    .or().like("display_name", term)
                    .or().like("student_no", term));
        }
        List<User> users = userMapper.selectList(wrapper);
        List<AdminUserView> views = users.stream().map(AdminUserView::from).toList();
        return ApiResponse.ok(views);
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminUserView> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        // Username validation
        String username = request.username().trim();
        if (username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!username.matches("[A-Za-z0-9_]{3,32}")) {
            throw new IllegalArgumentException("用户名只能包含字母、数字、下划线，长度 3-32 位");
        }
        Long duplicateUsername = userMapper.selectCount(new QueryWrapper<User>()
                .eq("username", username)
                .ne("id", id));
        if (duplicateUsername > 0) {
            throw new IllegalArgumentException("用户名已被使用");
        }

        // Email uniqueness validation
        String email = request.email().trim();
        if (email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        Long duplicateEmail = userMapper.selectCount(new QueryWrapper<User>()
                .eq("email", email)
                .ne("id", id));
        if (duplicateEmail > 0) {
            throw new IllegalArgumentException("邮箱已被其他用户使用");
        }

        // Parse Class (studentNo)
        String studentNo = request.studentNo();
        if (studentNo != null && !studentNo.isBlank()) {
            studentNo = studentNo.trim();
        } else {
            studentNo = null;
        }

        // Self-lockout validation
        CurrentUser currentUser = SecurityUtils.currentUser();
        if (currentUser.id().equals(id)) {
            if (request.role() != Role.SUPER_ADMIN) {
                throw new IllegalArgumentException("不能将自己的角色修改为非超级管理员");
            }
            if (Boolean.FALSE.equals(request.enabled())) {
                throw new IllegalArgumentException("不能禁用自己");
            }
        }

        // Validate password if provided
        String password = request.password();
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw new IllegalArgumentException("密码至少 6 位");
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        // Update fields
        user.setUsername(username);
        user.setDisplayName(request.displayName().trim());
        user.setEmail(email);
        user.setStudentNo(studentNo);
        user.setMajor(request.major() != null && !request.major().isBlank() ? request.major().trim() : null);
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.updateById(user);

        return ApiResponse.ok(AdminUserView.from(user));
    }

    public record UpdateUserRequest(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "昵称不能为空") String displayName,
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") String email,
            String studentNo,
            String major,
            @NotNull(message = "角色不能为空") Role role,
            @NotNull(message = "启用状态不能为空") Boolean enabled,
            String password
    ) {}

    public record AdminUserView(
            Long id,
            String username,
            String email,
            String displayName,
            String avatarUrl,
            String studentNo,
            String major,
            String role,
            Boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static AdminUserView from(User user) {
            return new AdminUserView(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.getStudentNo(),
                    user.getMajor(),
                    user.getRole().name(),
                    user.getEnabled(),
                    user.getCreatedAt(),
                    user.getUpdatedAt()
            );
        }
    }
}
