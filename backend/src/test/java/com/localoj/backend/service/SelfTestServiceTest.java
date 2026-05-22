package com.localoj.backend.service;

import com.localoj.backend.gojudge.GoJudgeClient;
import com.localoj.backend.gojudge.GoJudgeResult;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.enums.Language;
import com.localoj.common.enums.Role;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.model.Problem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        SelfTestService service = new SelfTestService(problemMapper, goJudgeClient, sandboxSettingsService);

        SelfTestService.SelfTestResult result = service.run(
                new CurrentUser(1L, "u", Role.STUDENT),
                1L,
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

        SelfTestService service = new SelfTestService(problemMapper, goJudgeClient, sandboxSettingsService);

        SelfTestService.SelfTestResult result = service.run(
                new CurrentUser(1L, "u", Role.STUDENT),
                1L,
                Language.PYTHON,
                "print(",
                ""
        );

        assertEquals(Verdict.CE, result.verdict());
        assertEquals("SyntaxError\n", result.stderr());
    }

    private Problem problem() {
        Problem problem = new Problem();
        problem.setId(1L);
        problem.setVisible(Boolean.TRUE);
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitKb(262144);
        return problem;
    }

    private SandboxSettingsService sandboxSettingsService() {
        SandboxSettingsService service = mock(SandboxSettingsService.class);
        when(service.view()).thenReturn(new SandboxSettingsService.SandboxSettingsView(1, 1, 10000, 1024, 50));
        return service;
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
