package com.coderushoj.worker.service;

import com.coderushoj.common.model.TestCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class TestCaseDataReader {
    private final Path dataRoot;

    public TestCaseDataReader(@Value("${app.data-root:/data}") String dataRoot) {
        this.dataRoot = Paths.get(dataRoot).toAbsolutePath().normalize();
    }

    public String readInput(TestCase testCase) {
        return readCaseFile(testCase, testCase.getInputFile(), testCase.getInputText(), "input");
    }

    public String readExpectedOutput(TestCase testCase) {
        return readCaseFile(testCase, testCase.getOutputFile(), testCase.getExpectedOutput(), "output");
    }

    private String readCaseFile(TestCase testCase, String fileName, String fallback, String label) {
        if (testCase.getProblemId() == null || fileName == null || fileName.isBlank()) {
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

    private Path caseDir(Long problemId) {
        Path path = dataRoot.resolve("problems").resolve(String.valueOf(problemId)).resolve("cases").normalize();
        ensureInside(dataRoot, path);
        return path;
    }

    private Path safeResolve(Path directory, String fileName) {
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("Invalid test case file name");
        }
        Path root = directory.toAbsolutePath().normalize();
        Path resolved = root.resolve(fileName).normalize();
        ensureInside(root, resolved);
        return resolved;
    }

    private static void ensureInside(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Invalid test case path");
        }
    }
}
