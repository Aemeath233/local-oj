package com.coderushoj.backend.service;

import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.TestCase;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProblemPackageExportServiceTest {

    @Test
    void testGenerateExamplePackage() throws Exception {
        ProblemService problemService = mock(ProblemService.class);
        TestCaseFileStorage testCaseFileStorage = mock(TestCaseFileStorage.class);
        ProblemPackageExportService service = new ProblemPackageExportService(problemService, testCaseFileStorage);

        byte[] zipBytes = service.generateExamplePackage();
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        boolean hasConfig = false;
        boolean hasStatement = false;
        boolean hasCases = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("config.yml")) {
                    hasConfig = true;
                } else if (entry.getName().equals("statement.md")) {
                    hasStatement = true;
                } else if (entry.getName().startsWith("cases/") && entry.getName().endsWith(".in")) {
                    hasCases = true;
                }
            }
        }

        assertTrue(hasConfig);
        assertTrue(hasStatement);
        assertTrue(hasCases);
    }

    @Test
    void testExportSingleProblem() throws Exception {
        ProblemService problemService = mock(ProblemService.class);
        TestCaseFileStorage testCaseFileStorage = mock(TestCaseFileStorage.class);
        ProblemPackageExportService service = new ProblemPackageExportService(problemService, testCaseFileStorage);

        Problem problem = new Problem();
        problem.setId(1L);
        problem.setSlug("test-problem");
        problem.setTitle("Test Problem");
        problem.setDifficulty("Easy");
        problem.setVisible(true);

        TestCase tc1 = new TestCase();
        tc1.setCaseName("case1");
        tc1.setScore(100);
        tc1.setInputFile("1.in");
        tc1.setOutputFile("1.out");

        when(problemService.testCases(1L)).thenReturn(List.of(tc1));

        Path tempDir = Files.createTempDirectory("test-export");
        Path inputPath = tempDir.resolve("1.in");
        Files.writeString(inputPath, "test input");
        Path outputPath = tempDir.resolve("1.out");
        Files.writeString(outputPath, "test output");

        when(testCaseFileStorage.getCaseFilePath(1L, "1.in")).thenReturn(inputPath);
        when(testCaseFileStorage.getCaseFilePath(1L, "1.out")).thenReturn(outputPath);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportProblems(List.of(problem), out);

        byte[] zipBytes = out.toByteArray();
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        boolean hasConfig = false;
        boolean hasInput = false;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("config.yml")) {
                    hasConfig = true;
                } else if (entry.getName().equals("cases/1.in")) {
                    hasInput = true;
                }
            }
        }

        assertTrue(hasConfig);
        assertTrue(hasInput);
    }
}
