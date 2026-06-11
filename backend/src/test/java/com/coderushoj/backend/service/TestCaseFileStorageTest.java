package com.coderushoj.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestCaseFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    public void testImportFilesPairsSameBasenameAndStoresFiles() throws Exception {
        TestCaseFileStorage storage = new TestCaseFileStorage(tempDir.toString());

        List<MultipartFile> files = List.of(
                file("2.in", "input2"),
                file("1.out", "output1"),
                file("1.in", "input1"),
                file("2.ans", "output2"),
                file("3.in", "ignored")
        );

        List<TestCaseFileStorage.ImportedCase> testCases = storage.importFiles(files);

        assertEquals(2, testCases.size());
        assertNotNull(testCases.get(0).uploadToken());
        assertEquals(testCases.get(0).uploadToken(), testCases.get(1).uploadToken());

        assertEquals("1", testCases.get(0).name());
        assertEquals("1.in", testCases.get(0).inputFile());
        assertEquals("1.out", testCases.get(0).outputFile());
        assertEquals(6, testCases.get(0).inputSize());
        assertEquals(7, testCases.get(0).outputSize());
        assertEquals(50, testCases.get(0).score());

        assertEquals("2", testCases.get(1).name());
        assertEquals("2.in", testCases.get(1).inputFile());
        assertEquals("2.ans", testCases.get(1).outputFile());
        assertEquals(50, testCases.get(1).score());

        Path uploadDir = tempDir.resolve("uploads").resolve(testCases.get(0).uploadToken());
        assertEquals("input1", Files.readString(uploadDir.resolve("1.in")));
        assertEquals("output2", Files.readString(uploadDir.resolve("2.ans")));
    }

    @Test
    public void testImportFileContentsPairsMemoryFiles() throws Exception {
        TestCaseFileStorage storage = new TestCaseFileStorage(tempDir.toString());

        List<TestCaseFileStorage.CaseFileContent> files = List.of(
                content("sample.in", "4 5\n"),
                content("sample.out", "9\n"),
                content("extra.out", "ignored\n")
        );

        List<TestCaseFileStorage.ImportedCase> testCases = storage.importFileContents(files);

        assertEquals(1, testCases.size());
        assertEquals("sample", testCases.getFirst().name());
        assertEquals("sample.in", testCases.getFirst().inputFile());
        assertEquals("sample.out", testCases.getFirst().outputFile());
        assertEquals(4, testCases.getFirst().inputSize());
        assertEquals(2, testCases.getFirst().outputSize());

        Path uploadDir = tempDir.resolve("uploads").resolve(testCases.getFirst().uploadToken());
        assertEquals("4 5\n", Files.readString(uploadDir.resolve("sample.in")));
        assertEquals("9\n", Files.readString(uploadDir.resolve("sample.out")));
    }

    @Test
    public void testImportFilesDistributesRemainderAcrossScores() {
        TestCaseFileStorage storage = new TestCaseFileStorage(tempDir.toString());

        List<MultipartFile> files = List.of(
                file("1.in", "input1"),
                file("1.out", "output1"),
                file("2.in", "input2"),
                file("2.out", "output2"),
                file("3.in", "input3"),
                file("3.out", "output3")
        );

        List<TestCaseFileStorage.ImportedCase> testCases = storage.importFiles(files);

        assertEquals(3, testCases.size());
        assertEquals(34, testCases.get(0).score());
        assertEquals(33, testCases.get(1).score());
        assertEquals(33, testCases.get(2).score());
        assertEquals(100, testCases.stream().mapToInt(TestCaseFileStorage.ImportedCase::score).sum());
    }

    private MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private TestCaseFileStorage.CaseFileContent content(String name, String content) {
        return new TestCaseFileStorage.CaseFileContent(name, content.getBytes(StandardCharsets.UTF_8));
    }
}
