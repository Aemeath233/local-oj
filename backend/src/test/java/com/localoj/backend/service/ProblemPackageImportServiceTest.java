package com.localoj.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProblemPackageImportServiceTest {

    @Test
    public void testParseConfigReadsMarkdownKeyValueMetadata() {
        ProblemPackageImportService.PackageConfig config = ProblemPackageImportService.parseConfig("""
                # Package metadata

                - title: ZIP Import Problem
                - slug: zip-import-problem
                - difficulty: Medium
                - tags: 数学, 前缀和
                - time_limit_ms: 1500
                - memory-limit-kb: 131072
                - visible: hidden
                - samples: 1, sample-2
                - scores: 1=20; sample-2=80
                """);

        assertEquals("ZIP Import Problem", config.title());
        assertEquals("zip-import-problem", config.slug());
        assertEquals("Medium", config.difficulty());
        assertEquals("数学, 前缀和", config.tags());
        assertEquals(1500, config.timeLimitMs());
        assertEquals(131072, config.memoryLimitKb());
        assertFalse(config.visible());
        assertTrue(config.samples().contains("1"));
        assertTrue(config.samples().contains("sample-2"));
        assertEquals(20, config.scores().get("1"));
        assertEquals(80, config.scores().get("sample-2"));
    }

    @Test
    public void testParseConfigProvidesDefaults() {
        ProblemPackageImportService.PackageConfig config = ProblemPackageImportService.parseConfig("""
                title: Minimal Problem
                """);

        assertEquals("Minimal Problem", config.title());
        assertEquals("", config.slug());
        assertEquals("Easy", config.difficulty());
        assertEquals("", config.tags());
        assertEquals(1000, config.timeLimitMs());
        assertEquals(262144, config.memoryLimitKb());
        assertTrue(config.visible());
        assertTrue(config.samples().isEmpty());
        assertTrue(config.scores().isEmpty());
    }

    @Test
    public void testPreviewPackageReadsMetadataAndCases() throws Exception {
        ProblemPackageImportService service = new ProblemPackageImportService(null, null);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "preview-problem.zip",
                "application/zip",
                zipBytes()
        );

        ProblemPackageImportService.PackagePreview preview = service.previewPackage(file);

        assertEquals("Preview Problem", preview.title());
        assertEquals("preview-problem", preview.slug());
        assertFalse(preview.slugGenerated());
        assertEquals("statement.md", preview.statementFile());
        assertEquals(2, preview.cases().size());
        assertEquals("1", preview.cases().get(0).name());
        assertEquals(20, preview.cases().get(0).score());
        assertTrue(preview.cases().get(0).sample());
        assertEquals(80, preview.cases().get(1).score());
    }

    private static byte[] zipBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addEntry(zip, "config.yml", """
                    slug: preview-problem
                    title: Preview Problem
                    samples: [1]
                    scores: 1=20,2=80
                    """);
            addEntry(zip, "statement.md", "# Preview Problem\n");
            addEntry(zip, "cases/1.in", "1 2\n");
            addEntry(zip, "cases/1.out", "3\n");
            addEntry(zip, "cases/2.in", "2 3\n");
            addEntry(zip, "cases/2.out", "5\n");
        }
        return output.toByteArray();
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

}

