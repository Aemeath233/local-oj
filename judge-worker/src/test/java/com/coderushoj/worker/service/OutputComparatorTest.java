package com.coderushoj.worker.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {
    private final OutputComparator comparator = new OutputComparator();

    @Test
    void ignoresTrailingSpacesAndFinalBlankLines() {
        assertThat(comparator.matches("1 2  \n3\n\n", "1 2\n3\n")).isTrue();
    }

    @Test
    void preservesMeaningfulWhitespaceInsideLines() {
        assertThat(comparator.matches("1  2\n", "1 2\n")).isFalse();
    }

    @Test
    void normalizesWindowsLineEndings() {
        assertThat(comparator.matches("hello\r\nworld\r\n", "hello\nworld\n")).isTrue();
    }
}
