package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserImportServiceTest {

    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private AdminUserImportService importService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(any())).thenReturn("encoded_pass");
        importService = new AdminUserImportService(userMapper, passwordEncoder);
    }

    @Test
    void importUsersFailsIfFileEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> importService.importUsers(file));
    }

    @Test
    void importUsersSuccessWithHeader() throws IOException {
        String csv = "用户名,邮箱,昵称,学号\n" +
                "alice,alice@test.com,Alice,123\n" +
                "bob,,Bob,456\n";
        MockMultipartFile file = new MockMultipartFile("file", csv.getBytes());

        when(userMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        AdminUserImportService.ImportUserResult result = importService.importUsers(file);

        assertEquals(2, result.total);
        assertEquals(2, result.successCount);
        assertEquals(0, result.failedCount);
        assertEquals("alice", result.users.get(0).username);
        assertEquals("bob", result.users.get(1).username);
    }

    @Test
    void importUsersHandlesValidationErrors() throws IOException {
        String csv = "username,email,password\n" +
                "al,test@test.com,123456\n" + // username too short
                "valid_user,invalid_email,123456\n" + // invalid email
                "duplicate,test@test.com,123456\n"; // duplicate username

        when(userMapper.selectCount(any(QueryWrapper.class))).thenAnswer(invocation -> {
            QueryWrapper wrapper = invocation.getArgument(0);
            if (wrapper.getSqlSegment().contains("username = #{ew.paramNameValuePairs.MPGENVAL1}")) {
                // assume duplicate is already in DB
                if (wrapper.getParamNameValuePairs().containsValue("duplicate")) {
                    return 1L;
                }
            }
            return 0L;
        });

        MockMultipartFile file = new MockMultipartFile("file", csv.getBytes());
        AdminUserImportService.ImportUserResult result = importService.importUsers(file);

        assertEquals(3, result.total);
        assertEquals(0, result.successCount);
        assertEquals(3, result.failedCount);
        assertEquals(3, result.errors.size());
        assertEquals("用户名只能包含字母、数字、下划线，长度 3-32 位", result.errors.get(0).reason);
        assertEquals("邮箱格式不正确", result.errors.get(1).reason);
        assertEquals("用户名已被使用", result.errors.get(2).reason);
    }
}
