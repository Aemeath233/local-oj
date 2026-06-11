package com.coderushoj.backend.service;

import com.coderushoj.common.model.Problem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ProblemPackageImportService {
    private static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final DateTimeFormatter SLUG_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ProblemService problemService;
    private final TestCaseFileStorage testCaseFileStorage;

    public ProblemPackageImportService(ProblemService problemService, TestCaseFileStorage testCaseFileStorage) {
        this.problemService = problemService;
        this.testCaseFileStorage = testCaseFileStorage;
    }

    @Transactional
    public Problem importPackage(MultipartFile file) {
        PackageAnalysis analysis = analyzePackage(file);
        List<TestCaseFileStorage.CaseFileContent> caseFiles = analysis.caseFiles().stream()
                .map(entry -> new TestCaseFileStorage.CaseFileContent(entry.simpleName(), entry.bytes()))
                .toList();
        List<TestCaseFileStorage.ImportedCase> importedCases = testCaseFileStorage.importFileContents(caseFiles);
        if (importedCases.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中没有找到同名配对的 .in 和 .out/.ans 测试点");
        }

        List<ProblemService.TestCaseCommand> testCases = new ArrayList<>();
        for (TestCaseFileStorage.ImportedCase importedCase : importedCases) {
            testCases.add(new ProblemService.TestCaseCommand(
                    importedCase.uploadToken(),
                    importedCase.name(),
                    importedCase.inputFile(),
                    importedCase.outputFile(),
                    importedCase.inputSize(),
                    importedCase.outputSize(),
                    analysis.scores().getOrDefault(importedCase.name(), 0)
            ));
        }

        return problemService.createProblem(new ProblemService.CreateProblemCommand(
                analysis.resolvedSlug(),
                analysis.config().title(),
                analysis.statementEntry().text(),
                analysis.config().timeLimitMs(),
                analysis.config().memoryLimitKb(),
                analysis.config().difficulty(),
                analysis.config().tags(),
                analysis.config().visible(),
                testCases
        ));
    }

    public PackagePreview previewPackage(MultipartFile file) {
        PackageAnalysis analysis = analyzePackage(file);
        List<PackageCasePreview> cases = analysis.pairedCases().stream()
                .map(pairedCase -> new PackageCasePreview(
                        pairedCase.name(),
                        pairedCase.input().simpleName(),
                        pairedCase.output().simpleName(),
                        (long) pairedCase.input().bytes().length,
                        (long) pairedCase.output().bytes().length,
                        analysis.scores().getOrDefault(pairedCase.name(), 0)
                ))
                .toList();
        return new PackagePreview(
                analysis.originalFilename(),
                analysis.configEntry().path(),
                analysis.statementEntry().path(),
                analysis.statementEntry().text().length(),
                analysis.config().title(),
                analysis.resolvedSlug(),
                analysis.slugGenerated(),
                analysis.config().difficulty(),
                analysis.config().tags(),
                analysis.config().timeLimitMs(),
                analysis.config().memoryLimitKb(),
                analysis.config().visible(),
                cases,
                warningsFor(analysis, cases)
        );
    }

    private PackageAnalysis analyzePackage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择题目 ZIP 包");
        }
        String originalFilename = file.getOriginalFilename() == null ? "problem.zip" : file.getOriginalFilename();
        if (!originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new IllegalArgumentException("题目包必须是 .zip 文件");
        }

        List<PackageEntry> entries = readZip(file);
        PackageEntry configEntry = findConfig(entries);
        PackageEntry statementEntry = findStatement(entries, configEntry);
        PackageConfig config = parseConfig(configEntry.text());
        List<PackageEntry> caseFiles = caseFiles(entries, configEntry, statementEntry);
        List<PairedCase> pairedCases = pairCases(caseFiles);
        if (pairedCases.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中没有找到同名配对的 .in 和 .out/.ans 测试点");
        }

        Set<String> importedCaseNames = new HashSet<>();
        pairedCases.forEach(pairedCase -> importedCaseNames.add(pairedCase.name()));
        Map<String, Integer> resolvedScores = resolveScores(pairedCases.stream().map(PairedCase::name).toList(), config.scores(), importedCaseNames);
        boolean slugGenerated = config.slug().isBlank();
        String resolvedSlug = slugGenerated ? generatedSlug(originalFilename, config.title()) : config.slug();
        return new PackageAnalysis(
                originalFilename,
                configEntry,
                statementEntry,
                config,
                caseFiles,
                pairedCases,
                resolvedScores,
                resolvedSlug,
                slugGenerated
        );
    }

    private List<PackageEntry> readZip(MultipartFile file) {
        List<PackageEntry> entries = new ArrayList<>();
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalizePath(entry.getName());
                if (path.isBlank() || path.startsWith("__MACOSX/")) {
                    continue;
                }
                String simpleName = simpleFilename(path);
                String lowerName = simpleName.toLowerCase(Locale.ROOT);
                boolean isAllowed = isConfigName(simpleName)
                        || isStatementName(simpleName)
                        || (lowerName.endsWith(".md") && !lowerName.equalsIgnoreCase("readme.md"))
                        || lowerName.endsWith(".in")
                        || lowerName.endsWith(".out")
                        || lowerName.endsWith(".ans");
                if (!isAllowed) {
                    zip.closeEntry();
                    continue;
                }
                byte[] bytes = readEntry(zip);
                totalBytes += bytes.length;
                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("题目包解压后超过 512MB");
                }
                entries.add(new PackageEntry(path, simpleName, bytes));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("读取题目 ZIP 包失败", ex);
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("题目 ZIP 包为空");
        }
        return entries;
    }

    private byte[] readEntry(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long size = 0;
        int read;
        while ((read = inputStream.read(buffer)) >= 0) {
            size += read;
            if (size > MAX_ENTRY_BYTES) {
                throw new IllegalArgumentException("单个题目包文件超过 256MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private PackageEntry findConfig(List<PackageEntry> entries) {
        return entries.stream()
                .filter(entry -> isConfigName(entry.simpleName()))
                .min(Comparator.comparing(PackageEntry::path))
                .orElseThrow(() -> new IllegalArgumentException("题目包缺少 config.yml 或 config.yaml 配置文件"));
    }

    private PackageEntry findStatement(List<PackageEntry> entries, PackageEntry configEntry) {
        return entries.stream()
                .filter(entry -> !entry.path().equals(configEntry.path()))
                .filter(entry -> isStatementName(entry.simpleName()))
                .min(Comparator.comparing(PackageEntry::path))
                .or(() -> entries.stream()
                        .filter(entry -> !entry.path().equals(configEntry.path()))
                        .filter(entry -> entry.simpleName().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .filter(entry -> !entry.simpleName().equalsIgnoreCase("readme.md"))
                        .min(Comparator.comparing(PackageEntry::path)))
                .orElseThrow(() -> new IllegalArgumentException("题目包缺少 problem.md 或 statement.md"));
    }

    private List<PackageEntry> caseFiles(
            List<PackageEntry> entries,
            PackageEntry configEntry,
            PackageEntry statementEntry
        ) {
        Set<String> usedNames = new HashSet<>();
        List<PackageEntry> files = new ArrayList<>();
        for (PackageEntry entry : entries) {
            if (entry.path().equals(configEntry.path()) || entry.path().equals(statementEntry.path())) {
                continue;
            }
            String lowerName = entry.simpleName().toLowerCase(Locale.ROOT);
            if (!lowerName.endsWith(".in") && !lowerName.endsWith(".out") && !lowerName.endsWith(".ans")) {
                continue;
            }
            if (!usedNames.add(entry.simpleName())) {
                throw new IllegalArgumentException("题目包内测试点文件名重复: " + entry.simpleName());
            }
            files.add(entry);
        }
        return files;
    }

    private static List<PairedCase> pairCases(List<PackageEntry> caseFiles) {
        Map<String, PackageEntry> inputs = new HashMap<>();
        Map<String, PackageEntry> outputs = new HashMap<>();
        for (PackageEntry entry : caseFiles) {
            String name = entry.simpleName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".in")) {
                String base = name.substring(0, name.length() - 3);
                if (!base.isBlank()) {
                    if (inputs.containsKey(base)) {
                        throw new IllegalArgumentException("题目包内同一测试点存在多个输入文件: " + base);
                    }
                    inputs.put(base, entry);
                }
            } else if (lower.endsWith(".out")) {
                String base = name.substring(0, name.length() - 4);
                if (!base.isBlank()) {
                    if (outputs.containsKey(base)) {
                        throw new IllegalArgumentException("题目包内同一测试点存在多个输出文件: " + base);
                    }
                    outputs.put(base, entry);
                }
            } else if (lower.endsWith(".ans")) {
                String base = name.substring(0, name.length() - 4);
                if (!base.isBlank()) {
                    if (outputs.containsKey(base)) {
                        throw new IllegalArgumentException("题目包内同一测试点存在多个输出文件: " + base);
                    }
                    outputs.put(base, entry);
                }
            }
        }
        return inputs.keySet().stream()
                .filter(outputs::containsKey)
                .sorted(ProblemPackageImportService::compareCaseName)
                .map(name -> new PairedCase(name, inputs.get(name), outputs.get(name)))
                .toList();
    }

    private static String extractYaml(String text) {
        if (text == null) return "";
        // Match code blocks ```yaml ... ``` or ``` ... ```
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstLineBreak = cleaned.indexOf('\n');
            int lastBackticks = cleaned.lastIndexOf("```");
            if (firstLineBreak > 0 && lastBackticks > firstLineBreak) {
                return cleaned.substring(firstLineBreak + 1, lastBackticks).trim();
            }
        }
        // Also strip frontmatter delimiters --- if present
        if (cleaned.startsWith("---")) {
            int secondDash = cleaned.indexOf("---", 3);
            if (secondDash > 0) {
                return cleaned.substring(3, secondDash).trim();
            }
        }
        return cleaned;
    }

    private static String getYamlString(Map<String, Object> data, String key) {
        Object val = data.get(canonicalKey(key));
        return val == null ? "" : val.toString().trim();
    }

    private static String getYamlTags(Map<String, Object> data, String key) {
        Object val = data.get(canonicalKey(key));
        if (val == null) {
            return "";
        }
        if (val instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.joining(","));
        }
        return val.toString().trim();
    }

    private static int getYamlInt(Map<String, Object> data, List<String> keys, int defaultValue, int min, String label) {
        Object val = null;
        for (String key : keys) {
            val = data.get(canonicalKey(key));
            if (val != null) {
                break;
            }
        }
        if (val == null) {
            return defaultValue;
        }
        try {
            int parsed;
            if (val instanceof Number num) {
                parsed = num.intValue();
            } else {
                parsed = Integer.parseInt(val.toString().trim());
            }
            if (parsed < min) {
                throw new IllegalArgumentException(label + " 不能小于 " + min);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " 必须是整数");
        }
    }

    private static boolean getYamlBoolean(Map<String, Object> data, String key, boolean defaultValue) {
        Object val = data.get(canonicalKey(key));
        if (val == null) {
            return defaultValue;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        String str = val.toString().trim().toLowerCase(Locale.ROOT);
        return switch (str) {
            case "true", "yes", "1", "on", "visible", "可见" -> true;
            case "false", "no", "0", "off", "hidden", "隐藏" -> false;
            default -> throw new IllegalArgumentException("visible 必须是 true/false");
        };
    }

    private static Map<String, Integer> getYamlScores(Map<String, Object> data, String key) {
        Object val = data.get(canonicalKey(key));
        Map<String, Integer> scores = new HashMap<>();
        if (val == null) {
            return scores;
        }
        if (val instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    String k = entry.getKey().toString().trim();
                    int scoreVal;
                    if (entry.getValue() instanceof Number num) {
                        scoreVal = num.intValue();
                    } else {
                        scoreVal = Integer.parseInt(entry.getValue().toString().trim());
                    }
                    scores.put(k, scoreVal);
                }
            }
            return scores;
        }
        // Fallback to string parsing
        return parseScores(val.toString());
    }

    static PackageConfig parseConfig(String text) {
        String yamlContent = extractYaml(text);
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        Object parsedObj;
        try {
            parsedObj = yaml.load(yamlContent);
        } catch (Exception e) {
            throw new IllegalArgumentException("YAML 配置文件格式错误，无法解析", e);
        }

        Map<String, Object> rawData = new HashMap<>();
        if (parsedObj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    rawData.put(entry.getKey().toString(), entry.getValue());
                }
            }
        } else if (parsedObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() != null) {
                            rawData.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                }
            }
        }

        // Standardize keys (lowercase, no spaces/hyphens/underscores)
        Map<String, Object> canonicalData = new HashMap<>();
        for (Map.Entry<String, Object> entry : rawData.entrySet()) {
            canonicalData.put(canonicalKey(entry.getKey()), entry.getValue());
        }

        String title = getYamlString(canonicalData, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("配置文件缺少 title 字段");
        }

        String slug = getYamlString(canonicalData, "slug");
        String difficulty = defaultValue(getYamlString(canonicalData, "difficulty"), "Easy");
        
        // Handle tags: could be a string or a list!
        String tags = getYamlTags(canonicalData, "tags");

        int timeLimitMs = getYamlInt(canonicalData, List.of("timelimitms", "timelimit"), 1000, 100, "timeLimitMs");
        int memoryLimitKb = getYamlInt(canonicalData, List.of("memorylimitkb", "memorylimit"), 262144, 16384, "memoryLimitKb");
        boolean visible = getYamlBoolean(canonicalData, "visible", true);

        // Handle scores: could be a string or a map!
        Map<String, Integer> scores = getYamlScores(canonicalData, "scores");

        return new PackageConfig(
                title,
                slug,
                difficulty,
                tags,
                timeLimitMs,
                memoryLimitKb,
                visible,
                scores
        );
    }

    private static Map<String, Integer> resolveScores(
            List<String> caseNames,
            Map<String, Integer> explicitScores,
            Set<String> importedCaseNames
    ) {
        validateKnownNames(explicitScores.keySet(), importedCaseNames, "scores");
        int explicitTotal = explicitScores.values().stream().mapToInt(Integer::intValue).sum();
        if (explicitTotal > 100) {
            throw new IllegalArgumentException("scores 总分不能超过 100");
        }

        List<String> missingNames = caseNames.stream()
                .filter(name -> !explicitScores.containsKey(name))
                .toList();
        List<Integer> defaultScores = distributeScores(100 - explicitTotal, missingNames.size());

        Map<String, Integer> scores = new HashMap<>(explicitScores);
        for (int i = 0; i < missingNames.size(); i++) {
            scores.put(missingNames.get(i), defaultScores.get(i));
        }
        return scores;
    }

    private static List<String> warningsFor(PackageAnalysis analysis, List<PackageCasePreview> cases) {
        List<String> warnings = new ArrayList<>();
        if (analysis.slugGenerated()) {
            warnings.add("config 中未填写 slug，导入时会自动生成唯一 slug。");
        }
        if (analysis.config().scores().isEmpty()) {
            warnings.add("config 中未填写 scores，系统会把 100 分平均分配给所有测试点。");
        }
        int ignoredCaseFiles = analysis.caseFiles().size() - cases.size() * 2;
        if (ignoredCaseFiles > 0) {
            warnings.add("有 " + ignoredCaseFiles + " 个测试数据文件没有同名配对，导入时会被忽略。");
        }
        return warnings;
    }

    private static void validateKnownNames(Iterable<String> names, Set<String> knownNames, String label) {
        for (String name : names) {
            if (!knownNames.contains(name)) {
                throw new IllegalArgumentException(label + " 包含不存在的测试点: " + name);
            }
        }
    }

    private static List<Integer> distributeScores(int total, int count) {
        if (count <= 0) {
            return List.of();
        }
        int base = total / count;
        int remainder = total % count;
        List<Integer> scores = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            scores.add(base + (i < remainder ? 1 : 0));
        }
        return scores;
    }

    private static int compareCaseName(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException ignored) {
            return left.compareTo(right);
        }
    }

    private static Map<String, Integer> parseScores(String value) {
        Map<String, Integer> scores = new HashMap<>();
        if (value == null || value.isBlank()) {
            return scores;
        }
        String normalized = value.replace(';', ',');
        for (String part : normalized.split(",")) {
            String item = part.trim();
            if (item.isBlank()) {
                continue;
            }
            String[] pieces = item.contains("=") ? item.split("=", 2) : item.split(":", 2);
            if (pieces.length != 2 || pieces[0].trim().isBlank()) {
                throw new IllegalArgumentException("scores 格式应为 1=20,2=80");
            }
            scores.put(pieces[0].trim(), parseInteger(pieces[1].trim(), 0, 0, "score"));
        }
        return scores;
    }

    private static int parseInteger(String value, int defaultValue, int min, String label) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < min) {
                throw new IllegalArgumentException(label + " 不能小于 " + min);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " 必须是整数");
        }
    }

    private static String generatedSlug(String originalFilename, String title) {
        String base = originalFilename.replaceAll("(?i)\\.zip$", "");
        String slugBase = slugify(base);
        if (slugBase.isBlank()) {
            slugBase = slugify(title);
        }
        if (slugBase.isBlank()) {
            slugBase = "problem";
        }
        return slugBase + "-" + LocalDateTime.now().format(SLUG_TIME);
    }

    private static String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80).replaceAll("-+$", "");
    }

    private static String normalizePath(String name) {
        String path = name == null ? "" : name.replace('\\', '/').trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.contains("..") || path.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("题目包内文件路径不合法: " + name);
        }
        return path;
    }

    private static String simpleFilename(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static boolean isConfigName(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.equals("config.md")
                || lower.equals("problem.config.md")
                || lower.equals("metadata.md")
                || lower.endsWith(".config.md")
                || lower.equals("config.yaml")
                || lower.equals("config.yml")
                || lower.equals("problem.yaml")
                || lower.equals("problem.yml")
                || lower.equals("metadata.yaml")
                || lower.equals("metadata.yml")
                || lower.endsWith(".config.yaml")
                || lower.endsWith(".config.yml");
    }

    private static boolean isStatementName(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.equals("problem.md")
                || lower.equals("statement.md")
                || lower.endsWith(".statement.md");
    }

    private static String defaultValue(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String canonicalKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
    }

    private record PackageEntry(String path, String simpleName, byte[] bytes) {
        String text() {
            try {
                java.nio.charset.CharsetDecoder decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder();
                decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
                decoder.onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                String result = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                if (result.contains("\uFFFD")) {
                    throw new java.nio.charset.CharacterCodingException();
                }
                return result;
            } catch (java.nio.charset.CharacterCodingException e) {
                try {
                    return new String(bytes, java.nio.charset.Charset.forName("GBK"));
                } catch (Exception ex) {
                    return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
    }

    private record PairedCase(String name, PackageEntry input, PackageEntry output) {
    }

    private record PackageAnalysis(
            String originalFilename,
            PackageEntry configEntry,
            PackageEntry statementEntry,
            PackageConfig config,
            List<PackageEntry> caseFiles,
            List<PairedCase> pairedCases,
            Map<String, Integer> scores,
            String resolvedSlug,
            boolean slugGenerated
    ) {
    }

    public record PackageCasePreview(
            String name,
            String inputFile,
            String outputFile,
            Long inputSize,
            Long outputSize,
            Integer score
    ) {
    }

    public record PackagePreview(
            String filename,
            String configFile,
            String statementFile,
            Integer statementChars,
            String title,
            String slug,
            Boolean slugGenerated,
            String difficulty,
            String tags,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            Boolean visible,
            List<PackageCasePreview> cases,
            List<String> warnings
    ) {
    }

    record PackageConfig(
            String title,
            String slug,
            String difficulty,
            String tags,
            Integer timeLimitMs,
            Integer memoryLimitKb,
            Boolean visible,
            Map<String, Integer> scores
    ) {
    }
}
