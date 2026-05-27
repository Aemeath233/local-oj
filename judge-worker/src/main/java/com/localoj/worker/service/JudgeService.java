package com.localoj.worker.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.common.enums.Language;
import com.localoj.common.enums.SubmissionStatus;
import com.localoj.common.enums.Verdict;
import com.localoj.common.mapper.ProblemMapper;
import com.localoj.common.mapper.SubmissionCaseResultMapper;
import com.localoj.common.mapper.SubmissionMapper;
import com.localoj.common.mapper.TestCaseMapper;
import com.localoj.common.model.Problem;
import com.localoj.common.model.Submission;
import com.localoj.common.model.SubmissionCaseResult;
import com.localoj.common.model.TestCase;
import com.localoj.worker.gojudge.GoJudgeClient;
import com.localoj.worker.gojudge.GoJudgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JudgeService {
    private static final Logger log = LoggerFactory.getLogger(JudgeService.class);
    private static final long COMPILE_MEMORY_LIMIT_BYTES = 512L * 1024L * 1024L;

    private final SubmissionMapper submissionMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final SubmissionCaseResultMapper caseResultMapper;
    private final GoJudgeClient goJudgeClient;
    private final OutputComparator outputComparator;
    private final TestCaseDataReader testCaseDataReader;
    private final SandboxSettingsProvider sandboxSettingsProvider;
    private final WorkerSystemLogService systemLogService;

    public JudgeService(
            SubmissionMapper submissionMapper,
            ProblemMapper problemMapper,
            TestCaseMapper testCaseMapper,
            SubmissionCaseResultMapper caseResultMapper,
            GoJudgeClient goJudgeClient,
            OutputComparator outputComparator,
            TestCaseDataReader testCaseDataReader,
            SandboxSettingsProvider sandboxSettingsProvider,
            WorkerSystemLogService systemLogService
    ) {
        this.submissionMapper = submissionMapper;
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.caseResultMapper = caseResultMapper;
        this.goJudgeClient = goJudgeClient;
        this.outputComparator = outputComparator;
        this.testCaseDataReader = testCaseDataReader;
        this.sandboxSettingsProvider = sandboxSettingsProvider;
        this.systemLogService = systemLogService;
    }

    public void judge(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            systemLogService.warn("judge", "submission_missing", "判题任务对应提交不存在", submissionId, null, null, null);
            return;
        }
        if (submission.getStatus() == SubmissionStatus.FINISHED) {
            systemLogService.info(
                    "judge",
                    "submission_skipped",
                    "提交已完成，跳过重复判题任务",
                    submission.getId(),
                    submission.getProblemId(),
                    submission.getUserId(),
                    "verdict=" + submission.getVerdict()
            );
            return;
        }
        Problem problem = problemMapper.selectById(submission.getProblemId());
        List<TestCase> testCases = testCaseMapper.selectList(new QueryWrapper<TestCase>()
                .eq("problem_id", submission.getProblemId())
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        if (problem == null || testCases.isEmpty()) {
            systemLogService.warn(
                    "judge",
                    "problem_or_cases_missing",
                    "题目或测试点缺失，无法判题",
                    submission.getId(),
                    submission.getProblemId(),
                    submission.getUserId(),
                    "problemExists=" + (problem != null) + "; testCaseCount=" + testCases.size()
            );
            finish(submission, Verdict.IE, 0, 0L, 0L, "Problem or test cases are missing");
            return;
        }

        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setErrorMessage(null);
        submissionMapper.updateById(submission);
        caseResultMapper.delete(new QueryWrapper<SubmissionCaseResult>().eq("submission_id", submissionId));
        systemLogService.info(
                "judge",
                "started",
                "提交开始判题",
                submission.getId(),
                submission.getProblemId(),
                submission.getUserId(),
                "language=" + submission.getLanguage() + "; caseCount=" + testCases.size()
        );

        try {
            JudgeOutcome outcome = judgeWithGoJudge(submission, problem, testCases);
            finish(submission, outcome.verdict(), outcome.score(), outcome.timeMs(), outcome.memoryKb(), outcome.message());
        } catch (CompileFailedException ex) {
            systemLogService.warn("judge", "compile_failed", "编译失败", submission.getId(), submission.getProblemId(), submission.getUserId(), ex.getMessage());
            finish(submission, Verdict.CE, 0, 0L, 0L, ex.getMessage());
        } catch (Exception ex) {
            log.error("Internal judge error for submission {}", submissionId, ex);
            systemLogService.error("judge", "internal_error", "判题内部异常", submission.getId(), submission.getProblemId(), submission.getUserId(), ex);
            throw new RuntimeException("System-level judge error: " + ex.getMessage(), ex);
        }
    }

    public void failSubmissionPermanently(Long submissionId, String message, Throwable ex) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return;
        }
        finish(submission, Verdict.IE, 0, 0L, 0L, message);
    }

    private JudgeOutcome judgeWithGoJudge(Submission submission, Problem problem, List<TestCase> testCases) {
        SandboxSettingsProvider.Settings settings = sandboxSettingsProvider.current();
        CompiledArtifact artifact = compileIfNeeded(submission, settings);
        try {
            int score = 0;
            long maxTimeMs = 0;
            long maxMemoryKb = 0;
            Verdict finalVerdict = Verdict.AC;
            String finalMessage = null;

            for (int i = 0; i < testCases.size(); i++) {
                TestCase testCase = testCases.get(i);
                String stdin = testCaseDataReader.readInput(testCase);
                String expectedOutput = testCaseDataReader.readExpectedOutput(testCase);
                CaseRun caseRun = runCase(submission, problem, artifact, stdin, settings);
                Verdict verdict = caseRun.verdict();
                if (verdict == Verdict.AC && outputComparator.matches(caseRun.stdout(), expectedOutput)) {
                    score += testCase.getScore() == null ? 0 : testCase.getScore();
                } else if (verdict == Verdict.AC) {
                    verdict = Verdict.WA;
                }

                maxTimeMs = Math.max(maxTimeMs, caseRun.timeMs());
                maxMemoryKb = Math.max(maxMemoryKb, caseRun.memoryKb());
                insertCaseResult(submission.getId(), testCase, i + 1, verdict, caseRun);

                if (verdict != Verdict.AC && finalVerdict == Verdict.AC) {
                    finalVerdict = verdict;
                    finalMessage = caseRun.message();
                }
            }

            return new JudgeOutcome(finalVerdict, finalVerdict == Verdict.AC ? 100 : score, maxTimeMs, maxMemoryKb, finalMessage);
        } finally {
            artifact.cachedFileIds().forEach(goJudgeClient::deleteFile);
        }
    }

    private CompiledArtifact compileIfNeeded(Submission submission, SandboxSettingsProvider.Settings settings) {
        return switch (submission.getLanguage()) {
            case C -> compileNative(submission, settings, "main.c", "main", List.of("/usr/bin/gcc", "-O2", "-pipe", "main.c", "-o", "main"));
            case CPP -> compileNative(submission, settings, "main.cpp", "main", List.of("/usr/bin/g++", "-std=c++20", "-O2", "-pipe", "main.cpp", "-o", "main"));
            case CPP_O3 -> compileNative(submission, settings, "main.cpp", "main", List.of("/usr/bin/g++", "-std=c++20", "-O3", "-pipe", "main.cpp", "-o", "main"));
            case PYTHON -> compilePython(submission, settings);
            case JAVA -> compileJava(submission, settings);
            case PYPY3 -> compilePyPy3(submission, settings);
        };
    }

    private CompiledArtifact compileNative(Submission submission, SandboxSettingsProvider.Settings settings, String sourceName, String binaryName, List<String> args) {
        Map<String, Object> cmd = command(args);
        cmd.put("files", standardFiles("", settings.outputLimitBytes()));
        cmd.put("cpuLimit", settings.compileCpuLimitNs());
        cmd.put("clockLimit", settings.compileClockLimitNs());
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", settings.maxProcessCount());
        cmd.put("copyIn", Map.of(sourceName, memoryFile(submission.getSourceCode())));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        cmd.put("copyOutCached", List.of(binaryName));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        String fileId = result.getFileIds() == null ? null : result.getFileIds().get(binaryName);
        if (fileId == null) {
            throw new CompileFailedException("Compiler did not produce executable");
        }
        return new CompiledArtifact(submission.getLanguage(), Map.of(binaryName, fileId), List.of(fileId));
    }

    private CompiledArtifact compilePython(Submission submission, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/python3", "-m", "py_compile", "main.py"));
        cmd.put("files", standardFiles("", settings.outputLimitBytes()));
        cmd.put("cpuLimit", settings.compileCpuLimitNs());
        cmd.put("clockLimit", settings.compileClockLimitNs());
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", settings.maxProcessCount());
        cmd.put("copyIn", Map.of("main.py", memoryFile(submission.getSourceCode())));
        cmd.put("copyOut", List.of("stdout", "stderr"));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        return new CompiledArtifact(Language.PYTHON, Map.of(), List.of());
    }

    private CompiledArtifact compilePyPy3(Submission submission, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/pypy3", "-m", "py_compile", "main.py"));
        cmd.put("files", standardFiles("", settings.outputLimitBytes()));
        cmd.put("cpuLimit", settings.compileCpuLimitNs());
        cmd.put("clockLimit", settings.compileClockLimitNs());
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", settings.maxProcessCount());
        cmd.put("copyIn", Map.of("main.py", memoryFile(submission.getSourceCode())));
        cmd.put("copyOut", List.of("stdout", "stderr"));

        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        ensureCompileAccepted(result);
        return new CompiledArtifact(Language.PYPY3, Map.of(), List.of());
    }

    private CompiledArtifact compileJava(Submission submission, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("/bin/bash", "-c", "/usr/bin/javac Main.java && /usr/bin/jar cf Main.jar *.class"));
        cmd.put("files", standardFiles("", settings.outputLimitBytes()));
        cmd.put("cpuLimit", settings.compileCpuLimitNs());
        cmd.put("clockLimit", settings.compileClockLimitNs());
        cmd.put("memoryLimit", COMPILE_MEMORY_LIMIT_BYTES);
        cmd.put("procLimit", settings.maxProcessCount());
        cmd.put("copyIn", Map.of("Main.java", memoryFile(submission.getSourceCode())));
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

    private CaseRun runCase(Submission submission, Problem problem, CompiledArtifact artifact, String stdin, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = switch (submission.getLanguage()) {
            case C, CPP, CPP_O3 -> nativeRunCommand(artifact, stdin, problem, settings);
            case PYTHON -> pythonRunCommand(submission, stdin, problem, settings);
            case JAVA -> javaRunCommand(artifact, stdin, problem, settings);
            case PYPY3 -> pypy3RunCommand(submission, stdin, problem, settings);
        };
        GoJudgeResult result = singleResult(goJudgeClient.run(List.of(cmd)));
        Verdict verdict = verdictFrom(result);
        String stdout = file(result, "stdout");
        String stderr = file(result, "stderr");
        String message = result.getError() != null ? result.getError() : stderr;
        return new CaseRun(verdict, nsToMs(result.getRunTime()), bytesToKb(result.getMemory()), stdout, stderr, message);
    }

    private Map<String, Object> nativeRunCommand(CompiledArtifact artifact, String stdin, Problem problem, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("./main"));
        cmd.put("files", standardFiles(stdin, settings.outputLimitBytes()));
        putRunLimits(cmd, problem, settings);
        cmd.put("copyIn", Map.of("main", preparedFile(artifact.fileIds().get("main"))));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private Map<String, Object> pythonRunCommand(Submission submission, String stdin, Problem problem, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/python3", "main.py"));
        cmd.put("files", standardFiles(stdin, settings.outputLimitBytes()));
        putRunLimits(cmd, problem, settings);
        cmd.put("copyIn", Map.of("main.py", memoryFile(submission.getSourceCode())));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private Map<String, Object> pypy3RunCommand(Submission submission, String stdin, Problem problem, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/pypy3", "main.py"));
        cmd.put("files", standardFiles(stdin, settings.outputLimitBytes()));
        putRunLimits(cmd, problem, settings);
        cmd.put("copyIn", Map.of("main.py", memoryFile(submission.getSourceCode())));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private Map<String, Object> javaRunCommand(CompiledArtifact artifact, String stdin, Problem problem, SandboxSettingsProvider.Settings settings) {
        Map<String, Object> cmd = command(List.of("/usr/bin/java", "-cp", "Main.jar", "Main"));
        cmd.put("files", standardFiles(stdin, settings.outputLimitBytes()));
        putRunLimits(cmd, problem, settings);
        cmd.put("copyIn", Map.of("Main.jar", preparedFile(artifact.fileIds().get("Main.jar"))));
        cmd.put("copyOut", List.of("stdout", "stderr"));
        return cmd;
    }

    private void putRunLimits(Map<String, Object> cmd, Problem problem, SandboxSettingsProvider.Settings settings) {
        long timeLimitNs = Math.max(100, problem.getTimeLimitMs()) * 1_000_000L;
        long clockLimitNs = timeLimitNs * 2 + 1_000_000_000L;
        cmd.put("cpuLimit", timeLimitNs);
        cmd.put("clockLimit", clockLimitNs);
        cmd.put("memoryLimit", Math.max(16_384, problem.getMemoryLimitKb()) * 1024L);
        cmd.put("procLimit", settings.maxProcessCount());
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

    private void insertCaseResult(Long submissionId, TestCase testCase, int caseIndex, Verdict verdict, CaseRun caseRun) {
        SubmissionCaseResult result = new SubmissionCaseResult();
        result.setSubmissionId(submissionId);
        result.setTestCaseId(testCase.getId());
        result.setCaseIndex(caseIndex);
        result.setVerdict(verdict);
        result.setTimeMs(caseRun.timeMs());
        result.setMemoryKb(caseRun.memoryKb());
        result.setStdoutText(trimForStorage(caseRun.stdout()));
        result.setStderrText(trimForStorage(caseRun.stderr()));
        result.setMessage(trimForStorage(caseRun.message()));
        result.setCreatedAt(LocalDateTime.now());
        caseResultMapper.insert(result);
    }

    private void finish(Submission submission, Verdict verdict, int score, long timeMs, long memoryKb, String message) {
        submission.setStatus(SubmissionStatus.FINISHED);
        submission.setVerdict(verdict);
        submission.setScore(Math.max(0, Math.min(100, score)));
        submission.setTimeMs(timeMs);
        submission.setMemoryKb(memoryKb);
        submission.setErrorMessage(trimForStorage(message));
        submission.setJudgedAt(LocalDateTime.now());
        submissionMapper.updateById(submission);
        String details = "score=" + submission.getScore()
                + "; timeMs=" + timeMs
                + "; memoryKb=" + memoryKb
                + "; message=" + trimForStorage(message);
        if (verdict == Verdict.AC) {
            systemLogService.info("judge", "finished", "判题完成: " + verdict, submission.getId(), submission.getProblemId(), submission.getUserId(), details);
        } else if (verdict == Verdict.IE) {
            systemLogService.errorDetails("judge", "finished", "判题完成: " + verdict, submission.getId(), submission.getProblemId(), submission.getUserId(), details);
        } else {
            systemLogService.warn("judge", "finished", "判题完成: " + verdict, submission.getId(), submission.getProblemId(), submission.getUserId(), details);
        }
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

    private String trimForStorage(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4096 ? value : value.substring(0, 4096);
    }

    private record CompiledArtifact(Language language, Map<String, String> fileIds, List<String> cachedFileIds) {
    }

    private record CaseRun(Verdict verdict, long timeMs, long memoryKb, String stdout, String stderr, String message) {
    }

    private record JudgeOutcome(Verdict verdict, int score, long timeMs, long memoryKb, String message) {
    }

    private static class CompileFailedException extends RuntimeException {
        CompileFailedException(String message) {
            super(message);
        }
    }
}
