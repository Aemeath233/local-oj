package com.coderushoj.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coderushoj.common.enums.Language;
import com.coderushoj.common.enums.SubmissionStatus;
import com.coderushoj.common.mapper.*;
import com.coderushoj.common.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PlagiarismCheckService {
    private static final Logger log = LoggerFactory.getLogger(PlagiarismCheckService.class);

    private final ContestMapper contestMapper;
    private final ContestProblemMapper contestProblemMapper;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final PlagiarismCheckMapper plagiarismCheckMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.jplag.jar-path}")
    private String jplagJarPath;

    @Value("${app.jplag.workspace}")
    private String jplagWorkspace;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public PlagiarismCheckService(
            ContestMapper contestMapper,
            ContestProblemMapper contestProblemMapper,
            ProblemMapper problemMapper,
            SubmissionMapper submissionMapper,
            UserMapper userMapper,
            PlagiarismCheckMapper plagiarismCheckMapper,
            ObjectMapper objectMapper
    ) {
        this.contestMapper = contestMapper;
        this.contestProblemMapper = contestProblemMapper;
        this.problemMapper = problemMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.plagiarismCheckMapper = plagiarismCheckMapper;
        this.objectMapper = objectMapper;
    }

    public void runPlagiarismCheckAsync(Long contestId) {
        executor.submit(() -> runCheck(contestId));
    }

    public List<PlagiarismCheck> getChecksByContest(Long contestId) {
        return plagiarismCheckMapper.selectList(
                new LambdaQueryWrapper<PlagiarismCheck>()
                        .eq(PlagiarismCheck::getContestId, contestId)
        );
    }

    private void runCheck(Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        if (contest == null) {
            log.error("Contest {} not found, aborting plagiarism check", contestId);
            return;
        }

        // 1. Verify JPlag executable JAR
        File jarFile = new File(jplagJarPath);
        if (!jarFile.exists()) {
            log.error("JPlag JAR not found at: {}", jplagJarPath);
            failAllProblems(contestId, "JPlag 查重引擎未在服务器就绪。请确认已下载 '" + jplagJarPath + "'。");
            return;
        }

        // 2. Fetch all problems in this contest
        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
        );

        if (cpList.isEmpty()) {
            log.warn("No problems in contest {}, plagiarism check completed immediately", contestId);
            return;
        }

        for (ContestProblem cp : cpList) {
            Long problemId = cp.getProblemId();
            try {
                processProblem(contestId, problemId);
            } catch (Exception e) {
                log.error("Error running plagiarism check for contest {} problem {}", contestId, problemId, e);
            }
        }
    }

    private void processProblem(Long contestId, Long problemId) {
        // Fetch all finished submissions for this problem in this contest
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getContestId, contestId)
                        .eq(Submission::getProblemId, problemId)
                        .eq(Submission::getStatus, SubmissionStatus.FINISHED)
        );

        if (submissions.isEmpty()) {
            markAllFamiliesFailed(contestId, problemId, "本题在此场比赛中没有任何提交代码");
            return;
        }

        // Group by user, keep only the latest submission per user
        Map<Long, Submission> latestUserSubmissions = new HashMap<>();
        for (Submission sub : submissions) {
            Submission existing = latestUserSubmissions.get(sub.getUserId());
            if (existing == null || sub.getCreatedAt().isAfter(existing.getCreatedAt())) {
                latestUserSubmissions.put(sub.getUserId(), sub);
            }
        }

        // Group submissions by JPlag language parser family
        Map<String, List<Submission>> familyGroups = latestUserSubmissions.values().stream()
                .collect(Collectors.groupingBy(this::getLanguageFamily));

        // Defined families: cpp, java, python3
        String[] families = {"cpp", "java", "python3"};
        for (String family : families) {
            List<Submission> familySubs = familyGroups.getOrDefault(family, Collections.emptyList());
            if (familySubs.size() < 2) {
                // If there are fewer than 2 submissions in this family, delete existing record or mark failed
                deleteOrCreateFailedCheck(contestId, problemId, family, "进行查重分析需要本语言家族至少有2位选手的提交代码");
                continue;
            }

            try {
                runJPlagForFamily(contestId, problemId, family, familySubs);
            } catch (Exception e) {
                log.error("JPlag failed for family {} in contest {} problem {}", family, contestId, problemId, e);
                saveCheckStatus(contestId, problemId, family, "FAILED", null, "运行查重引擎出错: " + e.getMessage());
            }
        }
    }

    private void runJPlagForFamily(Long contestId, Long problemId, String family, List<Submission> submissions) throws IOException, InterruptedException {
        // 1. Initialize DB status to RUNNING
        saveCheckStatus(contestId, problemId, family, "RUNNING", null, null);

        // 2. Prepare directories
        File baseDir = new File(jplagWorkspace, "contest_" + contestId + "/problem_" + problemId);
        File submissionsDir = new File(baseDir, "submissions_" + family);
        File reportDir = new File(baseDir, "report_" + family);

        // Clean up previous runs
        deleteDirectory(submissionsDir);
        deleteDirectory(reportDir);
        submissionsDir.mkdirs();
        reportDir.mkdirs();

        // 3. Export student submissions
        Map<Long, String> usernameMap = getUsernameMap(submissions);
        for (Submission sub : submissions) {
            String username = usernameMap.getOrDefault(sub.getUserId(), "user_" + sub.getUserId());
            String safeName = username.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + sub.getId();
            
            File studentSubFolder = new File(submissionsDir, safeName);
            studentSubFolder.mkdirs();

            String ext = getFileExtension(sub.getLanguage());
            File codeFile = new File(studentSubFolder, "Submission" + ext);
            Files.writeString(codeFile.toPath(), sub.getSourceCode());
        }

        // 4. Execute JPlag
        // Options: java -jar jplag.jar -l <family> -r <reportDir> <submissionsDir>
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", jplagJarPath,
                "-l", family,
                "-r", reportDir.getAbsolutePath(),
                submissionsDir.getAbsolutePath()
        );
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = pb.start();
        boolean finished = process.waitFor(3, TimeUnit.MINUTES);

        // 5. Cleanup raw submissions folder immediately to save disk space
        deleteDirectory(submissionsDir);

        if (!finished) {
            process.destroyForcibly();
            saveCheckStatus(contestId, problemId, family, "FAILED", null, "查重任务执行超时（限制3分钟）");
            return;
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            saveCheckStatus(contestId, problemId, family, "FAILED", null, "查重引擎运行异常退出，退出码: " + exitCode);
            return;
        }

        // 5.1 Unzip the generated report zip and extract report viewer
        File zipFile = new File(reportDir.getAbsolutePath() + ".zip");
        if (zipFile.exists()) {
            try {
                deleteDirectory(reportDir);
                reportDir.mkdirs();
                unzip(zipFile, reportDir);
                zipFile.delete();
                // Extract report viewer static assets from the JPlag JAR
                extractReportViewer(new File(jplagJarPath), reportDir);
            } catch (Exception e) {
                log.error("Failed to unzip JPlag report zip file or extract report viewer", e);
                saveCheckStatus(contestId, problemId, family, "FAILED", null, "解压查重引擎报告或释放查看器失败: " + e.getMessage());
                return;
            }
        } else {
            saveCheckStatus(contestId, problemId, family, "FAILED", null, "查重引擎未生成报告压缩包。");
            return;
        }

        // 6. Parse max similarity from overview.json (JPlag v5)
        File overviewFile = new File(reportDir, "overview.json");
        Double maxSimilarity = 0.0;
        if (overviewFile.exists()) {
            try {
                JsonNode root = objectMapper.readTree(overviewFile);
                if (root.has("top_comparisons")) {
                    JsonNode topComparisons = root.get("top_comparisons");
                    if (topComparisons.isArray()) {
                        for (JsonNode comparison : topComparisons) {
                            if (comparison.has("similarities")) {
                                JsonNode sims = comparison.get("similarities");
                                if (sims.has("MAX")) {
                                    double sim = sims.get("MAX").asDouble();
                                    if (sim > maxSimilarity) {
                                        maxSimilarity = sim;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse JPlag overview.json", e);
            }
        }

        // Multiply by 100 to save as percentage (e.g. 0.85 -> 85.0)
        // Note: Check JPlag output. If it is already a percentage (like 85.0) in JSON, keep it.
        // Usually JPlag v5/6 writes similarity as a ratio [0.0, 1.0]. If it is <= 1.0, convert to percentage.
        if (maxSimilarity > 0 && maxSimilarity <= 1.0) {
            maxSimilarity = maxSimilarity * 100.0;
        }

        saveCheckStatus(contestId, problemId, family, "COMPLETED", maxSimilarity, null);
    }

    private String getLanguageFamily(Submission sub) {
        Language lang = sub.getLanguage();
        if (lang == Language.JAVA) {
            return "java";
        }
        if (lang == Language.PYTHON || lang == Language.PYPY3) {
            return "python3";
        }
        return "cpp"; // C, CPP, CPP_O3 fall under cpp
    }

    private String getFileExtension(Language lang) {
        if (lang == Language.JAVA) return ".java";
        if (lang == Language.PYTHON || lang == Language.PYPY3) return ".py";
        return ".cpp";
    }

    private Map<Long, String> getUsernameMap(List<Submission> submissions) {
        Set<Long> userIds = submissions.stream().map(Submission::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) return Collections.emptyMap();
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private void saveCheckStatus(Long contestId, Long problemId, String family, String status, Double maxSimilarity, String errorMsg) {
        PlagiarismCheck check = plagiarismCheckMapper.selectOne(
                new LambdaQueryWrapper<PlagiarismCheck>()
                        .eq(PlagiarismCheck::getContestId, contestId)
                        .eq(PlagiarismCheck::getProblemId, problemId)
                        .eq(PlagiarismCheck::getLanguageFamily, family)
        );

        LocalDateTime now = LocalDateTime.now();
        if (check == null) {
            check = new PlagiarismCheck();
            check.setContestId(contestId);
            check.setProblemId(problemId);
            check.setLanguageFamily(family);
            check.setStatus(status);
            check.setMaxSimilarity(maxSimilarity);
            check.setErrorMessage(errorMsg);
            check.setCreatedAt(now);
            check.setUpdatedAt(now);
            plagiarismCheckMapper.insert(check);
        } else {
            check.setStatus(status);
            check.setMaxSimilarity(maxSimilarity);
            check.setErrorMessage(errorMsg);
            check.setUpdatedAt(now);
            plagiarismCheckMapper.updateById(check);
        }
    }

    private void deleteOrCreateFailedCheck(Long contestId, Long problemId, String family, String message) {
        // We set status to FAILED or delete it. Setting status to FAILED with message is clearer for the admin.
        saveCheckStatus(contestId, problemId, family, "FAILED", null, message);
    }

    private void markAllFamiliesFailed(Long contestId, Long problemId, String message) {
        String[] families = {"cpp", "java", "python3"};
        for (String family : families) {
            saveCheckStatus(contestId, problemId, family, "FAILED", null, message);
        }
    }

    private void failAllProblems(Long contestId, String message) {
        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
        );
        for (ContestProblem cp : cpList) {
            markAllFamiliesFailed(contestId, cp.getProblemId(), message);
        }
    }

    private void deleteDirectory(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDirectory(f);
                }
            }
            dir.delete();
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = newFile(destDir, entry);
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private File newFile(File destinationDir, java.util.zip.ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator) && !destFilePath.equals(destDirPath)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }
        return destFile;
    }

    private void extractReportViewer(File jarFile, File destDir) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("report-viewer/")) {
                    String relativePath = name.substring("report-viewer/".length());
                    if (relativePath.isEmpty()) continue;
                    
                    File targetFile = new File(destDir, relativePath);
                    if (entry.isDirectory()) {
                        targetFile.mkdirs();
                    } else {
                        File parent = targetFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        
                        if ("index.html".equals(relativePath)) {
                            try (java.io.InputStream is = zip.getInputStream(entry)) {
                                String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                content = content.replace("src=\"/assets/", "src=\"assets/");
                                content = content.replace("href=\"/assets/", "href=\"assets/");
                                content = content.replace("href=\"/favicon.ico\"", "href=\"favicon.ico\"");
                                Files.writeString(targetFile.toPath(), content);
                            }
                        } else {
                            try (java.io.InputStream is = zip.getInputStream(entry);
                                 java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                                byte[] buffer = new byte[1024];
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
