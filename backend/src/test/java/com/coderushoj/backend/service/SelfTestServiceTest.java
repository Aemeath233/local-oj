package com.coderushoj.backend.service;

import com.coderushoj.common.gojudge.GoJudgeClient;
import com.coderushoj.common.gojudge.GoJudgeResult;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.common.enums.Language;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.enums.Verdict;
import com.coderushoj.common.mapper.ContestMapper;
import com.coderushoj.common.mapper.ContestProblemMapper;
import com.coderushoj.common.mapper.ContestRegistrationMapper;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.model.Contest;
import com.coderushoj.common.model.ContestProblem;
import com.coderushoj.common.model.Problem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelfTestServiceTest {

    @Test
    void runReturnsStdoutWithoutCreatingSubmission() {
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        GoJudgeClient goJudgeClient = mock(GoJudgeClient.class);
        SandboxSettingsService sandboxSettingsService = sandboxSettingsService();
        when(problemMapper.selectById(1L)).thenReturn(problem());
        when(goJudgeClient.run(anyList())).thenReturn(
                List.of(acceptedCompile()),
                List.of(acceptedRun("3\n", "", 4_000_000L, 1_024L))
        );

        SelfTestService service = service(problemMapper, goJudgeClient, sandboxSettingsService);

        SelfTestService.SelfTestResult result = service.run(
                new CurrentUser(1L, "u", Role.STUDENT),
                1L,
                null,
                Language.PYTHON,
                "print(sum(map(int, input().split())))",
                "1 2\n"
        );

        assertEquals(Verdict.AC, result.verdict());
        assertEquals("3\n", result.stdout());
        assertEquals(4L, result.timeMs());
        assertEquals(1L, result.memoryKb());
    }

    @Test
    void runReturnsCompilationError() {
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        GoJudgeClient goJudgeClient = mock(GoJudgeClient.class);
        SandboxSettingsService sandboxSettingsService = sandboxSettingsService();
        when(problemMapper.selectById(1L)).thenReturn(problem());
        when(goJudgeClient.run(anyList())).thenReturn(List.of(failedCompile("SyntaxError\n")));

        SelfTestService service = service(problemMapper, goJudgeClient, sandboxSettingsService);

        SelfTestService.SelfTestResult result = service.run(
                new CurrentUser(1L, "u", Role.STUDENT),
                1L,
                null,
                Language.PYTHON,
                "print(",
                ""
        );

        assertEquals(Verdict.CE, result.verdict());
        assertEquals("SyntaxError\n", result.stderr());
    }

    @Test
    void contestSelfTestAllowsRegisteredUserForHiddenProblem() {
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        ContestMapper contestMapper = mock(ContestMapper.class);
        ContestProblemMapper contestProblemMapper = mock(ContestProblemMapper.class);
        ContestRegistrationMapper contestRegistrationMapper = mock(ContestRegistrationMapper.class);
        GoJudgeClient goJudgeClient = mock(GoJudgeClient.class);
        SandboxSettingsService sandboxSettingsService = sandboxSettingsService();

        Problem problem = problem();
        problem.setVisible(Boolean.FALSE);
        when(problemMapper.selectById(1L)).thenReturn(problem);
        when(contestMapper.selectById(9L)).thenReturn(runningContest());
        when(contestProblemMapper.selectOne(any())).thenReturn(new ContestProblem(9L, 1L, 0));
        when(contestRegistrationMapper.selectCount(any())).thenReturn(1L);
        when(goJudgeClient.run(anyList())).thenReturn(
                List.of(acceptedCompile()),
                List.of(acceptedRun("3\n", "", 4_000_000L, 1_024L))
        );

        SelfTestService service = new SelfTestService(
                problemMapper,
                contestMapper,
                contestProblemMapper,
                contestRegistrationMapper,
                goJudgeClient,
                sandboxSettingsService,
                mockRedisTemplate()
        );

        SelfTestService.SelfTestResult result = service.run(
                new CurrentUser(1L, "u", Role.STUDENT),
                1L,
                9L,
                Language.PYTHON,
                "print(sum(map(int, input().split())))",
                "1 2\n"
        );

        assertEquals(Verdict.AC, result.verdict());
    }

    private Problem problem() {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setVisible(Boolean.TRUE);
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitKb(262144);
        return problem;
    }

    private Contest runningContest() {
        Contest contest = new Contest();
        contest.setId(9L);
        contest.setVisible(Boolean.TRUE);
        contest.setStartTime(LocalDateTime.now().minusMinutes(5));
        contest.setEndTime(LocalDateTime.now().plusMinutes(30));
        return contest;
    }

    private SandboxSettingsService sandboxSettingsService() {
        SandboxSettingsService service = mock(SandboxSettingsService.class);
        when(service.view()).thenReturn(new SandboxSettingsService.SandboxSettingsView(1, 1, 10000, 1024, 50, 1));
        return service;
    }

    private SelfTestService service(
            ProblemMapper problemMapper,
            GoJudgeClient goJudgeClient,
            SandboxSettingsService sandboxSettingsService
    ) {
        return new SelfTestService(
                problemMapper,
                mock(ContestMapper.class),
                mock(ContestProblemMapper.class),
                mock(ContestRegistrationMapper.class),
                goJudgeClient,
                sandboxSettingsService,
                mockRedisTemplate()
        );
    }

    @SuppressWarnings("unchecked")
    private org.springframework.data.redis.core.StringRedisTemplate mockRedisTemplate() {
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any())).thenReturn(Boolean.TRUE);
        return redisTemplate;
    }

    private GoJudgeResult acceptedCompile() {
        GoJudgeResult result = new GoJudgeResult();
        result.setStatus("Accepted");
        result.setExitStatus(0);
        result.setFiles(Map.of("stdout", "", "stderr", ""));
        return result;
    }

    private GoJudgeResult failedCompile(String stderr) {
        GoJudgeResult result = new GoJudgeResult();
        result.setStatus("Accepted");
        result.setExitStatus(1);
        result.setFiles(Map.of("stdout", "", "stderr", stderr));
        return result;
    }

    private GoJudgeResult acceptedRun(String stdout, String stderr, Long runTime, Long memory) {
        GoJudgeResult result = new GoJudgeResult();
        result.setStatus("Accepted");
        result.setExitStatus(0);
        result.setRunTime(runTime);
        result.setMemory(memory);
        result.setFiles(Map.of("stdout", stdout, "stderr", stderr));
        return result;
    }
}
