package com.localoj.backend.service;

import org.junit.jupiter.api.Test;

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
}
