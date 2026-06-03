package com.localoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.gojudge.GoJudgeClient;
import com.localoj.backend.gojudge.GoJudgeResult;
import com.localoj.backend.security.CurrentUser;
import com.localoj.common.enums.Language;
import com.localoj.common.enums.Role;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ContestMapper;
import com.localoj.common.mapper.ContestProblemMapper;
import com.localoj.common.mapper.ContestRegistrationMapper;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.model.Contest;
import com.localoj.common.model.ContestProblem;
import com.localoj.common.model.ContestRegistration;
import com.localoj.common.model.Problem;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SelfTestService {
    private static final long COMPILE_MEMORY_LIMIT_BYTES = 512L * 1024L * 1024L;
    private static final int RESPONSE_TEXT_LIMIT = 20_000;

    private final ProblemMapper problemMapper;
    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ContestRegistrationMapper contestRegistrationMapper;
    private final GoJudgeClient goJudgeClient;
    private final SandboxSettingsService sandboxSettingsService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public SelfTestService(
            ProblemMapper problemMapper,
            ContestMapper contestMapper,
            ContestProblemMapper contestProblemMapper,
            ContestRegistrationMapper contestRegistrationMapper,
            GoJudgeClient goJudgeClient,
            SandboxSettingsService sandboxSettingsService,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate
    ) {
        this.problemMapper = problemMapper;
        this.contestMapper = contestMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.contestRegistrationMapper = contestRegistrationMapper;
        this.goJudgeClient = goJudgeClient;
        this.sandboxSettingsService = sandboxSettingsService;
        this.redisTemplate = redisTemplate;
    }

    public void checkAndApplyCooldown(CurrentUser user, Long problemId) {
        if (!isAdmin(user)) {
            String redisKey = "cooldown:problem:" + problemId + ":user:" + user.id();
            Boolean success = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", java.time.Duration.ofSeconds(5));
            if (success == null || !success) {
                throw new IllegalArgumentException("提交过于频繁，该题目每 5 秒仅允许提交或自测一次！");
            }
        }
    }

    public SelfTestResult run(CurrentUser user, Long problemId, Long contestId, Language language, String sourceCode, String stdin) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new IllegalArgumentException("Problem not found");
        }
        if (contestId == null) {
            if (!isAdmin(user) && !Boolean.TRUE.equals(problem.getVisible())) {
                throw new IllegalArgumentException("Problem not found");
            }
        } else {
            requireContestProblemAccess(user, contestId, problemId);
        }

        CompiledArtifact artifact = null;
        SandboxSettingsService.SandboxSettingsView sandboxSettings = sandboxSettingsService.view();
        try {
            artifact = compile(language, sourceCode, sandboxSettings);
            CaseRun caseRun = runCase(language, sourceCode, problem, artifact, stdin, sandboxSettings);
            return new SelfTestResult(
                    caseRun.verdict(),
                    caseRun.timeMs(),
                    caseRun.memoryKb(),
                    trimForResponse(caseRun.stdout()),
                    trimForResponse(caseRun.stderr()),
                    trimForResponse(caseRun.message())
            );
        } catch (CompileFailedException ex) {
            return new SelfTestResult(Verdict.CE, 0L, 0L, "", trimForResponse(ex.getMessage()), trimForResponse(ex.getMessage()));
        } finally {
            if (artifact != null) {
                artifact.cachedFileIds().forEach(goJudgeClient::deleteFile);
            }
        }
    }

    private void requireContestProblemAccess(CurrentUser user, Long contestId, Long problemId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null || (!Boolean.TRUE.equals(contest.getVisible()) && !isAdmin(user))) {
            throw new IllegalArgumentException("Contest not found");
        }
        ContestProblem contestProblem = contestProblemMapper.selectOne(new QueryWrapper<ContestProblem>()
                .eq("contest_id", contestId)
                .eq("problem_id", problemId));
        if (contestProblem == null) {
            throw new IllegalArgumentException("Problem not in this contest");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(contest.getStartTime()) && !isAdmin(user)) {
            throw new IllegalArgumentException("Contest has not started yet");
        }
        if (!isAdmin(user) && now.isBefore(contest.getEndTime()) && !isRegistered(contestId, user.id())) {
            throw new IllegalArgumentException("请先报名比赛");
        }
    }

    private boolean isRegistered(Long contestId, Long userId) {
        Long count = contestRegistrationMapper.selectCount(new QueryWrapper<ContestRegistration>()
                .eq("contest_id", contestId)
                .eq("user_id", userId));
        return count != null && count > 0;
    }

    private boolean isAdmin(CurrentUser user) {
        return user != null && (user.role() == Role.ADMIN || user.role() == Role.SUPER_ADMIN);
    }

    private CompiledArtifact compile(Language language, String sourceCode, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        return switch (language) {
            case C -> compileNative(language, sourceCode, sandboxSettings, "main.c", "main", List.of("/usr/bin/gcc", "-O2", "-pipe", "main.c", "-o", "main"));
            case CPP -> compileNative(language, sourceCode, sandboxSettings, "main.cpp", "main", List.of("/usr/bin/g++", "-std=c++20", "-O2", "-pipe", "main.cpp", "-o", "main"));
            case CPP_O3 -> compileNative(language, sourceCode, sandboxSettings, "main.cpp", "main", List.of("/usr/bin/g++", "-std=c++20", "-O3", "-pipe", "main.cpp", "-o", "main"));
            case PYTHON -> compilePython(sourceCode, sandboxSettings);
            case JAVA -> compileJava(sourceCode, sandboxSettings);
            case PYPY3 -> compilePyPy3(sourceCode, sandboxSettings);
        };
    }

    private CompiledArtifact compileNative(
            Language language,
            String sourceCode,
            SandboxSettingsService.SandboxSettingsView sandboxSettings,
            String sourceName,
            String binaryName,
            List<String> args
    ) {
        Map<String, Object> cmd = command(args);
        cmd.put("files", standardFiles("", outputLimitBytes(sandboxSettings)));
        cmd.put("cpuLimit", compileCpuLimitNs(sandboxSettings));
        cmd.put("clockLimit", compileClockLimitNs(sandboxSettings));
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", sandboxSettings.maxProcessCount());
        cmd.put("copyIn", Map.of(sourceName, memoryFile(sourceCode)));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        cmd.put("copyOutCached", List.of(binaryName));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        String fileId = result.getFileIds() == null ? null : result.getFileIds().get(binaryName);
        if (fileId == null) {
            throw new CompileFailedException("Compiler did not produce executable");
        }
        return new CompiledArtifact(language, Map.of(binaryName, fileId), List.of(fileId));
    }

    private CompiledArtifact compilePython(String sourceCode, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/python3", "-m", "py_compile", "main.py"));
        cmd.put("files", standardFiles("", outputLimitBytes(sandboxSettings)));
        cmd.put("cpuLimit", compileCpuLimitNs(sandboxSettings));
        cmd.put("clockLimit", compileClockLimitNs(sandboxSettings));
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", sandboxSettings.maxProcessCount());
        cmd.put("copyIn", Map.of("main.py", memoryFile(sourceCode)));
        cmd.put("copyOut", List.of("stdout", "stderr"));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        return new CompiledArtifact(Language.PYTHON, Map.of(), List.of());
    }

    private CompiledArtifact compilePyPy3(String sourceCode, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/pypy3", "-m", "py_compile", "main.py"));
        cmd.put("files", standardFiles("", outputLimitBytes(sandboxSettings)));
        cmd.put("cpuLimit", compileCpuLimitNs(sandboxSettings));
        cmd.put("clockLimit", compileClockLimitNs(sandboxSettings));
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", sandboxSettings.maxProcessCount());
        cmd.put("copyIn", Map.of("main.py", memoryFile(sourceCode)));
        cmd.put("copyOut", List.of("stdout", "stderr"));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        return new CompiledArtifact(Language.PYPY3, Map.of(), List.of());
    }

    private CompiledArtifact compileJava(String sourceCode, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("/bin/bash", "-c", "/usr/bin/javac Main.java && /usr/bin/jar cf Main.jar *.class"));
        cmd.put("files", standardFiles("", outputLimitBytes(sandboxSettings)));
        cmd.put("cpuLimit", compileCpuLimitNs(sandboxSettings));
        cmd.put("clockLimit", compileClockLimitNs(sandboxSettings));
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", sandboxSettings.maxProcessCount());
        cmd.put("copyIn", Map.of("Main.java", memoryFile(sourceCode)));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        cmd.put("copyOutCached", List.of("Main.jar"));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        String fileId = result.getFileIds() == null ? null : result.getFileIds().get("Main.jar");
        if (fileId == null) {
            throw new CompileFailedException("Compiler did not produce Main.jar");
        }
        return new CompiledArtifact(Language.JAVA, Map.of("Main.jar", fileId), List.of(fileId));
    }

    private CaseRun runCase(
            Language language,
            String sourceCode,
            Problem problem,
            CompiledArtifact artifact,
            String stdin,
            SandboxSettingsService.SandboxSettingsView sandboxSettings
    ) {
        Map<String, Object> cmd = switch (language) {
            case C, CPP, CPP_O3 -> nativeRunCommand(artifact, problem, stdin, sandboxSettings);
            case PYTHON -> pythonRunCommand(sourceCode, problem, stdin, sandboxSettings);
            case JAVA -> javaRunCommand(artifact, problem, stdin, sandboxSettings);
            case PYPY3 -> pypy3RunCommand(sourceCode, problem, stdin, sandboxSettings);
        };
        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        Verdict verdict = verdictFrom(result);
        String stdout = file(result, "stdout");
        String stderr = file(result, "stderr");
        String message = result.getError() != null ? result.getError() : stderr;
        return new CaseRun(verdict, nsToMs(result.getRunTime()), bytesToKb(result.getMemory()), stdout, stderr, message);
    }

    private Map<String, Object> nativeRunCommand(CompiledArtifact artifact, Problem problem, String stdin, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("./main"));
        cmd.put("files", standardFiles(stdin, outputLimitBytes(sandboxSettings)));
        putRunLimits(cmd, problem, sandboxSettings);
        cmd.put("copyIn", Map.of("main", preparedFile(artifact.fileIds().get("main"))));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private Map<String, Object> pythonRunCommand(String sourceCode, Problem problem, String stdin, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/python3", "main.py"));
        cmd.put("files", standardFiles(stdin, outputLimitBytes(sandboxSettings)));
        putRunLimits(cmd, problem, sandboxSettings);
        cmd.put("copyIn", Map.of("main.py", memoryFile(sourceCode)));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private Map<String, Object> pypy3RunCommand(String sourceCode, Problem problem, String stdin, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/pypy3", "main.py"));
        cmd.put("files", standardFiles(stdin, outputLimitBytes(sandboxSettings)));
        putRunLimits(cmd, problem, sandboxSettings);
        cmd.put("copyIn", Map.of("main.py", memoryFile(sourceCode)));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private Map<String, Object> javaRunCommand(CompiledArtifact artifact, Problem problem, String stdin, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/java", "-cp", "Main.jar", "Main"));
        cmd.put("files", standardFiles(stdin, outputLimitBytes(sandboxSettings)));
        putRunLimits(cmd, problem, sandboxSettings);
        cmd.put("copyIn", Map.of("Main.jar", preparedFile(artifact.fileIds().get("Main.jar"))));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private void putRunLimits(Map<String, Object> cmd, Problem problem, SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        long timeLimitNs = Math.max(100, problem.getTimeLimitMs()) * 1_000_000L;
        long clockLimitNs = timeLimitNs * 2 + 1_000_000_000L;
        cmd.put("cpuLimit", timeLimitNs);
        cmd.put("clockLimit", clockLimitNs);
        cmd.put("memoryLimit", Math.max(16_384, problem.getMemoryLimitKb()) * 1024L);
        cmd.put("procLimit", sandboxSettings.maxProcessCount());
    }

    private long outputLimitBytes(SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        return sandboxSettings.defaultOutputLimitKb() * 1024L;
    }

    private long compileCpuLimitNs(SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        return sandboxSettings.compileTimeoutMs() * 1_000_000L;
    }

    private long compileClockLimitNs(SandboxSettingsService.SandboxSettingsView sandboxSettings) {
        return compileCpuLimitNs(sandboxSettings) * 2 + 1_000_000_000L;
    }

    private void ensureCompileAccepted(GoJudgeResult result) {
        boolean accepted = "Accepted".equals(result.getStatus()) && Objects.equals(result.getExitStatus(), 0);
        if (!accepted) {
            String stderr = file(result, "stderr");
            String message = stderr.isBlank() ? result.getError() : stderr;
            throw new CompileFailedException(message == null || message.isBlank() ? "Compilation failed" : message);
        }
    }

    private Verdict verdictFrom(GoJudgeResult result) {
        if ("Accepted".equals(result.getStatus())) {
            return Objects.equals(result.getExitStatus(), 0) ? Verdict.AC : Verdict.RE;
        }
        return switch (result.getStatus() == null ? "" : result.getStatus()) {
            case "Time Limit Exceeded" -> Verdict.TLE;
            case "Memory Limit Exceeded" -> Verdict.MLE;
            case "Output Limit Exceeded" -> Verdict.OLE;
            case "Nonzero Exit Status", "Signalled" -> Verdict.RE;
            default -> Verdict.IE;
        };
    }

    private Map<String, Object> command(List<String> args) {
        Map<String, Object> cmd = new LinkedHashMap<>();
        cmd.put("args", args);
        cmd.put("env", List.of("PATH=/usr/bin:/bin", "LANG=C.UTF-8"));
        return cmd;
    }

    private List<Map<String, Object>> standardFiles(String stdin, long maxOutputBytes) {
        List<Map<String, Object>> files = new ArrayList<>();
        files.add(memoryFile(stdin == null ? "" : stdin));
        files.add(collector("stdout", maxOutputBytes));
        files.add(collector("stderr", maxOutputBytes));
        return files;
    }

    private Map<String, Object> memoryFile(String content) {
        return Map.of("content", content == null ? "" : content);
    }

    private Map<String, Object> preparedFile(String fileId) {
        return Map.of("fileId", fileId);
    }

    private Map<String, Object> collector(String name, long max) {
        return Map.of("name", name, "max", max);
    }

    private GoJudgeResult singleResult(List<GoJudgeResult> results) {
        if (results.isEmpty()) {
            throw new IllegalStateException("go-judge returned no result");
        }
        return results.getFirst();
    }

    private String file(GoJudgeResult result, String name) {
        if (result.getFiles() == null) {
            return "";
        }
        return result.getFiles().getOrDefault(name, "");
    }

    private long nsToMs(Long ns) {
        return ns == null ? 0L : Math.max(0L, Math.round(ns / 1_000_000.0));
    }

    private long bytesToKb(Long bytes) {
        return bytes == null ? 0L : Math.max(0L, (bytes + 1023L) / 1024L);
    }

    private String trimForResponse(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= RESPONSE_TEXT_LIMIT ? value : value.substring(0, RESPONSE_TEXT_LIMIT);
    }

    public record SelfTestResult(Verdict verdict, long timeMs, long memoryKb, String stdout, String stderr, String message) {
    }

    private record CompiledArtifact(Language language, Map<String, String> fileIds, List<String> cachedFileIds) {
    }

    private record CaseRun(Verdict verdict, long timeMs, long memoryKb, String stdout, String stderr, String message) {
    }

    private static class CompileFailedException extends RuntimeException {
        CompileFailedException(String message) {
            super(message);
        }
    }
}
