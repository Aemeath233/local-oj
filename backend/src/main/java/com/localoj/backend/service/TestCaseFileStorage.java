package com.localoj.backend.service;

import com.localoj.common.model.TestCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TestCaseFileStorage {
    private static final String INPUT_SUFFIX = ".in";
    private static final String OUTPUT_SUFFIX = ".out";
    private static final String ANSWER_SUFFIX = ".ans";

    private final Path dataRoot;
    private final Path uploadRoot;

    public TestCaseFileStorage(@Value("${app.data-root:/data}") String dataRoot) {
        this.dataRoot = Paths.get(dataRoot).toAbsolutePath().normalize();
        this.uploadRoot = this.dataRoot.resolve("uploads").normalize();
    }

    public List<ImportedCase> importFiles(List<MultipartFile> files) {
        return importParts(files.stream()
                .map(file -> (CasePart) new MultipartPart(file, simpleFilename(file.getOriginalFilename())))
                .toList());
    }

    public List<ImportedCase> importFileContents(List<CaseFileContent> files) {
        return importParts(files.stream()
                .map(file -> (CasePart) new MemoryPart(file, simpleFilename(file.filename())))
                .toList());
    }

    private List<ImportedCase> importParts(List<CasePart> files) {
        Map<String, CasePart> inputs = new HashMap<>();
        Map<String, OutputPart> outputs = new HashMap<>();

        for (CasePart file : files) {
            String filename = simpleFilename(file.filename());
            String lowerFilename = filename.toLowerCase();
            if (lowerFilename.endsWith(INPUT_SUFFIX)) {
                String baseName = filename.substring(0, filename.length() - INPUT_SUFFIX.length());
                if (!baseName.isBlank()) {
                    inputs.put(baseName, file);
                }
            } else if (lowerFilename.endsWith(OUTPUT_SUFFIX)) {
                String baseName = filename.substring(0, filename.length() - OUTPUT_SUFFIX.length());
                if (!baseName.isBlank()) {
                    outputs.put(baseName, new OutputPart(file, OUTPUT_SUFFIX));
                }
            } else if (lowerFilename.endsWith(ANSWER_SUFFIX)) {
                String baseName = filename.substring(0, filename.length() - ANSWER_SUFFIX.length());
                if (!baseName.isBlank()) {
                    outputs.put(baseName, new OutputPart(file, ANSWER_SUFFIX));
                }
            }
        }

        List<String> pairedNames = inputs.keySet().stream()
                .filter(outputs::containsKey)
                .sorted(TestCaseFileStorage::compareCaseName)
                .toList();
        if (pairedNames.isEmpty()) {
            return List.of();
        }

        String uploadToken = newToken();
        Path tokenDir = uploadRoot.resolve(uploadToken).normalize();
        ensureInside(uploadRoot, tokenDir);

        try {
            Files.createDirectories(tokenDir);
            Set<String> usedStems = new HashSet<>();
            List<ImportedCase> importedCases = new ArrayList<>();
            int caseIndex = 0;
            for (String caseName : pairedNames) {
                OutputPart outputPart = outputs.get(caseName);
                String stem = uniqueStem(sanitizeStem(caseName), usedStems);
                String inputFile = stem + INPUT_SUFFIX;
                String outputFile = stem + outputPart.suffix();
                long inputSize = savePart(inputs.get(caseName), tokenDir.resolve(inputFile));
                long outputSize = savePart(outputPart.file(), tokenDir.resolve(outputFile));
                importedCases.add(new ImportedCase(
                        uploadToken,
                        caseName,
                        inputFile,
                        outputFile,
                        inputSize,
                        outputSize,
                        distributedScore(caseIndex, pairedNames.size()),
                        false
                ));
                caseIndex++;
            }
            return importedCases;
        } catch (IOException ex) {
            deleteRecursivelyIfExists(tokenDir);
            throw new UncheckedIOException("保存测试点文件失败", ex);
        }
    }

    public List<TestCase> materializeCases(
            Long problemId,
            List<ProblemService.TestCaseCommand> commands,
            LocalDateTime now
    ) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Path caseDir = caseDir(problemId);
        Path problemDir = caseDir.getParent();
        Path stagingDir = problemDir.resolve("cases-" + newToken() + ".tmp").normalize();
        ensureInside(dataRoot, stagingDir);

        try {
            Files.createDirectories(stagingDir);
            Set<String> usedFiles = new HashSet<>();
            Set<String> uploadTokens = new HashSet<>();
            List<TestCase> testCases = new ArrayList<>();
            int index = 1;
            for (ProblemService.TestCaseCommand command : commands) {
                String inputFile = safeCaseFileName(command.inputFile(), INPUT_SUFFIX, "input");
                String outputFile = safeOutputFileName(command.outputFile());
                requireUniqueFile(inputFile, usedFiles);
                requireUniqueFile(outputFile, usedFiles);

                Path inputSource = sourceFile(problemId, command.uploadToken(), inputFile);
                Path outputSource = sourceFile(problemId, command.uploadToken(), outputFile);
                Path inputTarget = stagingDir.resolve(inputFile).normalize();
                Path outputTarget = stagingDir.resolve(outputFile).normalize();
                ensureInside(stagingDir, inputTarget);
                ensureInside(stagingDir, outputTarget);

                long inputSize = copyCaseFile(inputSource, inputTarget);
                long outputSize = copyCaseFile(outputSource, outputTarget);
                if (hasText(command.uploadToken())) {
                    uploadTokens.add(command.uploadToken());
                }

                TestCase testCase = new TestCase();
                testCase.setProblemId(problemId);
                testCase.setCaseName(normalizeCaseName(command.name(), inputFile));
                testCase.setInputFile(inputFile);
                testCase.setOutputFile(outputFile);
                testCase.setInputSize(inputSize);
                testCase.setOutputSize(outputSize);
                testCase.setInputText(null);
                testCase.setExpectedOutput(null);
                testCase.setScore(command.score() == null ? 100 : command.score());
                testCase.setSortOrder(index);
                testCase.setSample(Boolean.TRUE.equals(command.sample()));
                testCase.setCreatedAt(now);
                testCases.add(testCase);
                index++;
            }

            replaceDirectory(stagingDir, caseDir);
            for (String uploadToken : uploadTokens) {
                deleteRecursivelyIfExists(uploadRoot.resolve(uploadToken).normalize());
            }
            return testCases;
        } catch (IOException ex) {
            deleteRecursivelyIfExists(stagingDir);
            throw new UncheckedIOException("保存测试点文件失败", ex);
        }
    }

    public String readInput(TestCase testCase) {
        return readCaseFile(testCase, testCase.getInputFile(), testCase.getInputText(), "input");
    }

    public String readExpectedOutput(TestCase testCase) {
        return readCaseFile(testCase, testCase.getOutputFile(), testCase.getExpectedOutput(), "output");
    }

    private String readCaseFile(TestCase testCase, String fileName, String fallback, String label) {
        if (testCase.getProblemId() == null || !hasText(fileName)) {
            return fallback == null ? "" : fallback;
        }
        Path path = safeResolve(caseDir(testCase.getProblemId()), fileName);
        if (!Files.exists(path)) {
            if (fallback != null) {
                return fallback;
            }
            throw new IllegalStateException("Missing " + label + " file for test case " + testCase.getId());
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取测试点文件失败", ex);
        }
    }

    private Path sourceFile(Long problemId, String uploadToken, String fileName) {
        if (hasText(uploadToken)) {
            String safeToken = safeUploadToken(uploadToken);
            return safeResolve(uploadRoot.resolve(safeToken).normalize(), fileName);
        }
        return safeResolve(caseDir(problemId), fileName);
    }

    private long copyCaseFile(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("测试点文件不存在，请重新上传");
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return Files.size(target);
    }

    private void replaceDirectory(Path stagingDir, Path caseDir) throws IOException {
        Files.createDirectories(caseDir.getParent());
        Path backupDir = caseDir.resolveSibling("cases-" + newToken() + ".bak").normalize();
        ensureInside(dataRoot, backupDir);
        boolean hasBackup = false;
        try {
            if (Files.exists(caseDir)) {
                Files.move(caseDir, backupDir);
                hasBackup = true;
            }
            Files.move(stagingDir, caseDir);
            if (hasBackup) {
                deleteRecursivelyIfExists(backupDir);
            }
        } catch (IOException ex) {
            if (!Files.exists(caseDir) && hasBackup && Files.exists(backupDir)) {
                Files.move(backupDir, caseDir, StandardCopyOption.REPLACE_EXISTING);
            }
            throw ex;
        }
    }

    private long savePart(CasePart file, Path destination) throws IOException {
        ensureInside(uploadRoot, destination);
        try (InputStream inputStream = file.openStream()) {
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return Files.size(destination);
    }

    private Path caseDir(Long problemId) {
        Path path = dataRoot.resolve("problems").resolve(String.valueOf(problemId)).resolve("cases").normalize();
        ensureInside(dataRoot, path);
        return path;
    }

    private Path safeResolve(Path directory, String fileName) {
        Path root = directory.toAbsolutePath().normalize();
        Path resolved = root.resolve(safeCaseFileName(fileName, null, "case")).normalize();
        ensureInside(root, resolved);
        return resolved;
    }

    private String safeCaseFileName(String fileName, String requiredSuffix, String label) {
        if (!hasText(fileName) || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Invalid " + label + " file name");
        }
        String safeName = simpleFilename(fileName);
        String lowerName = safeName.toLowerCase();
        if (requiredSuffix != null && !lowerName.endsWith(requiredSuffix)) {
            throw new IllegalArgumentException("Invalid " + label + " file suffix");
        }
        if (requiredSuffix == null
                && !lowerName.endsWith(OUTPUT_SUFFIX)
                && !lowerName.endsWith(ANSWER_SUFFIX)
                && !lowerName.endsWith(INPUT_SUFFIX)) {
            throw new IllegalArgumentException("Invalid " + label + " file suffix");
        }
        return safeName;
    }

    private String safeOutputFileName(String fileName) {
        String safeName = safeCaseFileName(fileName, null, "output");
        String lowerName = safeName.toLowerCase();
        if (!lowerName.endsWith(OUTPUT_SUFFIX) && !lowerName.endsWith(ANSWER_SUFFIX)) {
            throw new IllegalArgumentException("Invalid output file suffix");
        }
        return safeName;
    }

    private String safeUploadToken(String uploadToken) {
        String token = uploadToken.trim();
        if (!token.matches("[A-Za-z0-9-]{16,64}")) {
            throw new IllegalArgumentException("Invalid upload token");
        }
        return token;
    }

    private static void requireUniqueFile(String fileName, Set<String> usedFiles) {
        if (!usedFiles.add(fileName)) {
            throw new IllegalArgumentException("测试点文件名重复: " + fileName);
        }
    }

    private static void ensureInside(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Invalid test case path");
        }
    }

    private void deleteRecursivelyIfExists(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        ensureInside(dataRoot, root);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String simpleFilename(String originalFilename) {
        String normalized = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static int compareCaseName(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException ignored) {
            return left.compareTo(right);
        }
    }

    private static String sanitizeStem(String caseName) {
        String stem = caseName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (stem.isBlank() || stem.equals(".") || stem.equals("..")) {
            stem = "case";
        }
        return stem.length() <= 120 ? stem : stem.substring(0, 120);
    }

    private static String uniqueStem(String stem, Set<String> usedStems) {
        String candidate = stem;
        int counter = 2;
        while (!usedStems.add(candidate)) {
            candidate = stem + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private static String normalizeCaseName(String caseName, String inputFile) {
        if (hasText(caseName)) {
            return caseName.trim();
        }
        return inputFile.substring(0, inputFile.length() - INPUT_SUFFIX.length());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public record ImportedCase(
            String uploadToken,
            String name,
            String inputFile,
            String outputFile,
            long inputSize,
            long outputSize,
            int score,
            boolean sample
    ) {
    }

    public record CaseFileContent(String filename, byte[] bytes) {
    }

    private interface CasePart {
        String filename();

        InputStream openStream() throws IOException;
    }

    private record MultipartPart(MultipartFile file, String filename) implements CasePart {
        @Override
        public InputStream openStream() throws IOException {
            return file.getInputStream();
        }
    }

    private record MemoryPart(CaseFileContent file, String filename) implements CasePart {
        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(file.bytes() == null ? new byte[0] : file.bytes());
        }
    }

    private record OutputPart(CasePart file, String suffix) {
    }

    public record TestCaseText(
            String inputText,
            String expectedOutput,
            Integer score,
            Boolean sample
    ) {
    }

    public List<ImportedCase> saveTextCases(List<TestCaseText> cases) {
        String uploadToken = newToken();
        Path tokenDir = uploadRoot.resolve(uploadToken).normalize();
        ensureInside(uploadRoot, tokenDir);
        try {
            Files.createDirectories(tokenDir);
            List<ImportedCase> importedCases = new ArrayList<>();
            int index = 1;
            for (TestCaseText caseText : cases) {
                String name = String.valueOf(index);
                String inputFile = name + INPUT_SUFFIX;
                String outputFile = name + OUTPUT_SUFFIX;
                Path inputPath = tokenDir.resolve(inputFile);
                Path outputPath = tokenDir.resolve(outputFile);

                Files.writeString(inputPath, caseText.inputText() == null ? "" : caseText.inputText(), StandardCharsets.UTF_8);
                Files.writeString(outputPath, caseText.expectedOutput() == null ? "" : caseText.expectedOutput(), StandardCharsets.UTF_8);

                importedCases.add(new ImportedCase(
                        uploadToken,
                        name,
                        inputFile,
                        outputFile,
                        Files.size(inputPath),
                        Files.size(outputPath),
                        caseText.score() == null ? distributedScore(index - 1, cases.size()) : caseText.score(),
                        Boolean.TRUE.equals(caseText.sample())
                ));
                index++;
            }
            return importedCases;
        } catch (IOException ex) {
            deleteRecursivelyIfExists(tokenDir);
            throw new UncheckedIOException("保存临时文本测试点失败", ex);
        }
    }

    public void deleteProblemDirectory(Long problemId) {
        Path problemDir = caseDir(problemId).getParent();
        deleteRecursivelyIfExists(problemDir);
    }

    public Path getCaseFilePath(Long problemId, String fileName) {
        Path directory = dataRoot.resolve("problems").resolve(String.valueOf(problemId)).resolve("cases").normalize();
        ensureInside(dataRoot, directory);
        Path root = directory.toAbsolutePath().normalize();
        Path resolved = root.resolve(fileName).normalize();
        ensureInside(root, resolved);
        return resolved;
    }

    private static int distributedScore(int index, int total) {
        if (total <= 0) {
            return 0;
        }
        int base = 100 / total;
        int remainder = 100 % total;
        return base + (index < remainder ? 1 : 0);
    }
}
