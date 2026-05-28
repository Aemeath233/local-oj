package com.localoj.backend.service;

import com.localoj.common.enums.Language;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class CodeFormatService {

    public String format(Language language, String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return sourceCode;
        }

        return switch (language) {
            case C, CPP, CPP_O3, JAVA -> formatCStyle(sourceCode);
            case PYTHON, PYPY3 -> formatPython(sourceCode);
        };
    }

    private String formatCStyle(String sourceCode) {
        String[] lines = sourceCode.split("\\R");
        List<String> formattedLines = new ArrayList<>();
        int indentLevel = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                // Keep at most one consecutive blank line
                if (formattedLines.isEmpty() || !formattedLines.getLast().isEmpty()) {
                    formattedLines.add("");
                }
                continue;
            }

            // Handle block comments
            if (inBlockComment) {
                formattedLines.add(" ".repeat(Math.max(0, indentLevel * 4)) + trimmed);
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("/*")) {
                formattedLines.add(" ".repeat(Math.max(0, indentLevel * 4)) + trimmed);
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }

            // Adjust indent level for closing braces on the current line
            int closeBraces = countOccurrences(trimmed, "}");
            int openBraces = countOccurrences(trimmed, "{");

            // If a line starts with a closing brace, we decrease indent for this line
            int currentLineIndent = indentLevel;
            if (trimmed.startsWith("}") || trimmed.startsWith(")")) {
                currentLineIndent = Math.max(0, indentLevel - 1);
            } else if (closeBraces > openBraces) {
                currentLineIndent = Math.max(0, indentLevel - (closeBraces - openBraces));
            }

            // Special indentation for switch labels (case/default)
            boolean isSwitchLabel = trimmed.startsWith("case ") || trimmed.startsWith("default:");
            if (isSwitchLabel && currentLineIndent > 0) {
                currentLineIndent = Math.max(0, currentLineIndent - 1);
            }

            // Build the formatted line
            String indent = " ".repeat(currentLineIndent * 4);
            
            // Standardize spaces around common C++ elements
            String processed = standardizeSpaces(trimmed);
            
            formattedLines.add(indent + processed);

            // Update indent level for subsequent lines
            indentLevel += (openBraces - closeBraces);
            if (indentLevel < 0) {
                indentLevel = 0;
            }
        }

        // Clean up leading/trailing empty lines
        while (!formattedLines.isEmpty() && formattedLines.getFirst().isEmpty()) {
            formattedLines.removeFirst();
        }
        while (!formattedLines.isEmpty() && formattedLines.getLast().isEmpty()) {
            formattedLines.removeLast();
        }

        return String.join("\n", formattedLines) + "\n";
    }

    private String formatPython(String sourceCode) {
        String[] lines = sourceCode.split("\\R");
        List<String> formattedLines = new ArrayList<>();

        for (String line : lines) {
            // Trim trailing spaces but preserve leading spaces (since indentation matters in Python!)
            String trimmedRight = rtrim(line);
            
            if (trimmedRight.trim().isEmpty()) {
                if (formattedLines.isEmpty() || !formattedLines.getLast().isEmpty()) {
                    formattedLines.add("");
                }
                continue;
            }

            // Standardize spacing around operators in Python
            String leadingSpaces = line.substring(0, line.indexOf(line.trim()));
            String processed = standardizeSpaces(trimmedRight.trim());
            
            formattedLines.add(leadingSpaces + processed);
        }

        while (!formattedLines.isEmpty() && formattedLines.getFirst().isEmpty()) {
            formattedLines.removeFirst();
        }
        while (!formattedLines.isEmpty() && formattedLines.getLast().isEmpty()) {
            formattedLines.removeLast();
        }

        return String.join("\n", formattedLines) + "\n";
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) != -1) {
            count++;
            index += target.length();
        }
        return count;
    }

    private String rtrim(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
            i--;
        }
        return s.substring(0, i + 1);
    }

    private String standardizeSpaces(String line) {
        if (line.startsWith("#") || line.startsWith("//") || line.startsWith("/*")) {
            return line;
        }
        
        String result = line;
        
        // Space around assignments and comparative operators
        result = result.replaceAll("\\s*==\\s*", " == ");
        result = result.replaceAll("\\s*!=\\s*", " != ");
        result = result.replaceAll("\\s*>=\\s*", " >= ");
        result = result.replaceAll("\\s*<=\\s*", " <= ");
        result = result.replaceAll("(?<![\\+\\-\\*\\/\\%\\&\\|\\^\\<\\>\\=!])\\s*=\\s*(?![=])", " = ");
        result = result.replaceAll("(?<![\\+\\-\\*\\/\\%\\&\\|\\^])\\s*\\+\\s*(?![\\+=])", " + ");
        result = result.replaceAll("(?<![\\+\\-\\*\\/\\%\\&\\|\\^])\\s*-\\s*(?![\\-=])", " - ");
        result = result.replaceAll("\\s*&&\\s*", " && ");
        result = result.replaceAll("\\s*\\|\\|\\s*", " || ");
        
        // Clean up redundant double spaces (if any)
        result = result.replaceAll(" {2,}", " ");
        
        // Fix spaces around brackets in control structures, e.g. if(a == b) -> if (a == b)
        result = result.replaceAll("\\b(if|for|while|switch)\\s*\\(", "$1 (");
        
        return result;
    }
}
