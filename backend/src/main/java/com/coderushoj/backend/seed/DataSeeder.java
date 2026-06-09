package com.coderushoj.backend.seed;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.TestCaseMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.TestCase;
import com.coderushoj.common.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataSeeder(
            UserMapper userMapper,
            ProblemMapper problemMapper,
            TestCaseMapper testCaseMapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword
    ) {
        this.userMapper = userMapper;
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedSampleProblem();
    }

    private void seedAdmin() {
        User existingAdmin = userMapper.selectOne(new QueryWrapper<User>().eq("username", adminUsername));
        if (existingAdmin != null) {
            if (existingAdmin.getRole() != Role.SUPER_ADMIN) {
                existingAdmin.setRole(Role.SUPER_ADMIN);
                userMapper.updateById(existingAdmin);
            }
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("Administrator");
        admin.setRole(Role.SUPER_ADMIN);
        admin.setEnabled(Boolean.TRUE);
        admin.setAuthTokenVersion(0);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userMapper.insert(admin);
    }

    private void seedSampleProblem() {
        Long count = problemMapper.selectCount(new QueryWrapper<Problem>().eq("slug", "a-plus-b"));
        if (count > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Problem problem = new Problem();
        problem.setSlug("a-plus-b");
        problem.setTitle("A + B Problem");
        problem.setDescription("""
                Given two integers `a` and `b`, output their sum.

                ## Input

                One line containing two integers `a` and `b`.

                ## Output

                Print one integer: `a + b`.

                ## Constraints

                - `-10^9 <= a, b <= 10^9`
                - Use 64-bit integer arithmetic if needed.
                """);
        problem.setInputDescription(null);
        problem.setOutputDescription(null);
        problem.setSampleInput(null);
        problem.setSampleOutput(null);
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitKb(262144);
        problem.setDifficulty("Easy");
        problem.setTags("warmup,math");
        problem.setVisible(Boolean.TRUE);
        problem.setCreatedAt(now);
        problem.setUpdatedAt(now);
        problemMapper.insert(problem);

        insertCase(problem.getId(), "1 2\n", "3\n", 50, 1, true, now);
        insertCase(problem.getId(), "100 -7\n", "93\n", 50, 2, false, now);
    }

    private void insertCase(Long problemId, String input, String output, Integer score, Integer order, Boolean sample, LocalDateTime now) {
        TestCase testCase = new TestCase();
        testCase.setProblemId(problemId);
        testCase.setInputText(input);
        testCase.setExpectedOutput(output);
        testCase.setScore(score);
        testCase.setSortOrder(order);
        testCase.setSample(sample);
        testCase.setCreatedAt(now);
        testCaseMapper.insert(testCase);
    }
}
