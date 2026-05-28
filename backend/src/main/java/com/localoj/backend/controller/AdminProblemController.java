package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.AdminService;
import com.localoj.backend.service.ProblemService;
import com.localoj.backend.service.ProblemPackageImportService;
import com.localoj.backend.service.TestCaseFileStorage;
import com.localoj.backend.service.TrainingService;
import com.localoj.common.model.TestCase;
import com.localoj.common.model.Problem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@RestController
@RequestMapping("/api/admin/problems")
public class AdminProblemController {
    private final ProblemService problemService;
    private final AdminService adminService;
    private final TestCaseFileStorage testCaseFileStorage;
    private final ProblemPackageImportService problemPackageImportService;
    private final TrainingService trainingService;

    public AdminProblemController(
            ProblemService problemService,
            AdminService adminService,
            TestCaseFileStorage testCaseFileStorage,
            ProblemPackageImportService problemPackageImportService,
            TrainingService trainingService
    ) {
        this.problemService = problemService;
        this.adminService = adminService;
        this.testCaseFileStorage = testCaseFileStorage;
        this.problemPackageImportService = problemPackageImportService;
        this.trainingService = trainingService;
    }

    @GetMapping
    public ApiResponse<List<AdminService.ProblemSummary>> list() {
        return ApiResponse.ok(adminService.problems());
    }

    @PostMapping
    public ApiResponse<AdminProblemDetail> create(
            @Valid @RequestBody CreateProblemRequest request,
            @RequestParam(value = "autolinkTrainingId", required = false) Long autolinkTrainingId
    ) {
        Problem problem = problemService.createProblem(request.toCommand());
        if (autolinkTrainingId != null) {
            trainingService.linkProblems(autolinkTrainingId, List.of(problem.getId()));
        }
        return ApiResponse.ok(detail(problem.getId()));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<AdminService.ProblemSummary> updateVisibility(
            @PathVariable("id") Long id,
            @Valid @RequestBody VisibilityRequest request
    ) {
        return ApiResponse.ok(adminService.updateProblemVisibility(id, request.visible()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProblemDetail> getProblem(@PathVariable("id") Long id) {
        return ApiResponse.ok(detail(id));
    }

    private AdminProblemDetail detail(Long id) {
        Problem problem = problemService.requireProblem(id);
        List<TestCase> testCases = problemService.testCases(id);
        return new AdminProblemDetail(problem, testCases);
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminProblemDetail> update(@PathVariable("id") Long id, @Valid @RequestBody CreateProblemRequest request) {
        problemService.updateProblem(id, request.toCommand());
        return ApiResponse.ok(detail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        problemService.deleteProblem(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/import-files")
    public ApiResponse<List<TestCaseFileStorage.ImportedCase>> importFiles(@RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(testCaseFileStorage.importFiles(files));
    }

    @PostMapping("/import-package")
    public ApiResponse<AdminProblemDetail> importPackage(@RequestParam("file") MultipartFile file) {
        Problem problem = problemPackageImportService.importPackage(file);
        return ApiResponse.ok(detail(problem.getId()));
    }

    @PostMapping("/import-package/preview")
    public ApiResponse<ProblemPackageImportService.PackagePreview> previewPackage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(problemPackageImportService.previewPackage(file));
    }

    @GetMapping("/example-package")
    public ResponseEntity<byte[]> examplePackage() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addZipText(zip, "config.yml", """
                    slug: a-plus-b
                    title: A + B Problem
                    difficulty: Easy
                    tags: [基础, 输入输出]
                    timeLimitMs: 1000
                    memoryLimitKb: 262144
                    visible: true
                    samples: [1]
                    """);
            addZipText(zip, "statement.md", """
                    Read two integers and output their sum.

                    ## Input

                    Two integers `a` and `b`.

                    ## Output

                    One integer, `a + b`.

                    ## Constraints

                    $1 \\le a, b \\le 10^9$
                    """);
            addZipText(zip, "README.md", """
                    # LocalOJ 题目导入包规范说明

                    本压缩包是一个标准的题目包示例，用于快速在后台导入题目。如果您使用 AI Agent（如 Claude, GPT）来为您自动批量生成题目包，可以把本规范作为提示词（Prompt）约束直接提供给 Agent 遵守。

                    ## ⚠️ 核心参数约束 (Core Constraints)

                    为了使导入后的题目在系统中完美契合并运行，请务必严格遵守以下参数约束：

                    1. **题目难度 (Difficulty)**:
                       - 题目难度只分为三挡：**`Easy`**、**`Medium`**、**`Hard`**。
                       - 请在配置文件中仅在这三者中选取一个，首字母务必大写。

                    2. **时间限制 (Time Limit)**:
                       - 如果没有特殊的时间限制要求，时间限制统一默认为 **`1000`** ms (即 1s)。
                       - 字段名：`timeLimitMs`。最低限制 `100`，最大限制依据系统配置（一般不超过 `10000`）。

                    3. **内存限制 (Memory Limit)**:
                       - 如果没有特殊的内存开销要求，内存限制统一默认为 **`262144`** KB (即 256MB)。
                       - 字段名：`memoryLimitKb`。最低限制 `16384` (16MB)。

                    4. **题面不要包含主标题 (No Double Title)**:
                       - **禁止**在 `statement.md` 的开头书写诸如 `# A + B Problem`、`# 题目名称` 的一级主标题！
                       - 因为系统在前台题目详情页面已经自动加载并渲染了在 `config.yml` 中定义的 `title`。如果题面文件里再写一遍标题，会导致前台页面顶部出现两个重叠的一模一样的标题，十分突兀和不美观。
                       - `statement.md` 直接以题目的背景/描述段落开始即可。

                    ---

                    ## 目录与文件说明 (Folder Structure)

                    1. **config.yml** (必须):
                       题目元数据配置文件。指定题目 Slug、标题、难度、标签、时间限制 (ms)、内存限制 (KB)、公开可见状态、分数分配方式以及充当样例展示的测试点名称。

                    2. **statement.md** (必须):
                       题面 Markdown 文档。支持标准的 Markdown 格式。
                       *注意*: 题面中不需要手动书写“样例”板块，系统会自动根据 `config.yml` 中配置的 `samples` 字段提取测试点内容并动态高亮渲染在前台“Samples”区域。

                    3. **cases/** 目录 (必须):
                       测试数据文件夹。包含成对的测试输入与输出文件。
                       * 每一个测试点需要有同名的输入文件 `.in` 和输出文件 `.out` 或 `.ans`（如 `1.in` 与 `1.out`）。

                    ---

                    ## 数学公式书写规范 (LaTeX Math Syntax)

                    为了防止 Markdown 解析器与数学公式中的特殊字符发生冲突（如 `_` 误识别为斜体，`^` 误识别为上标，`<=` 或 `<` 误识别为 HTML 标签），本系统支持标准的 **LaTeX** 数学公式语法，并通过 KaTeX 引擎在前端进行渲染：

                    * **行内公式 (Inline Math)**: 使用单个 `$` 包裹。例如：`$ 1 \\le n \\le 100000 $` 会被渲染为漂亮的上标/下标和关系符号。
                      *注意*: 起始 `$` 后面不能紧跟空格，结束 `$` 前面不能紧贴空格。
                    * **块级公式 (Block Math)**: 使用双个 `$$` 包裹，独占一行或单独展示。例如：`$$ -10^9 \\le a_i \\le 10^9 $$`。
                    * **特殊字符书写**: 请**避免**直接在普通 Markdown 文本中书写含有 `_`、`^`、`<=` 或 `<` 的复杂算式，务必将此类公式用 `$` 或 `$$` 包裹起来，或者使用 LaTeX 的对应转义符（如 `\\le` 代表小于等于 `≤`，`\\ge` 代表大于等于 `≥`）。

                    ---

                    ## 导入机制说明

                    * 导入过程中，除 `config.yml`、`statement.md` 和 `cases/` 下的成对 `.in`/`.out`/`.ans` 测试点文件外，本 `README.md` 文件或任何其他多余的不相干文件均会被后端服务**自动过滤并不予保存**，绝不占用任何额外的物理空间。
                    """);
            addZipText(zip, "cases/1.in", "1 2\n");
            addZipText(zip, "cases/1.out", "3\n");
            addZipText(zip, "cases/2.in", "40 2\n");
            addZipText(zip, "cases/2.out", "42\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=localoj-problem-package-example.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(output.toByteArray());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportProblems(@RequestParam("ids") List<Long> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要导出的题目");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String filename;

        if (ids.size() == 1) {
            // Export single problem
            Problem problem = problemService.requireProblem(ids.get(0));
            filename = problem.getSlug() + ".zip";
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                exportProblemToZip(problem, zip);
            }
        } else {
            // Export multiple problems into individual zips inside a parent zip
            filename = "problems-export-" + System.currentTimeMillis() + ".zip";
            try (ZipOutputStream parentZip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                for (Long id : ids) {
                    Problem problem = problemService.requireProblem(id);
                    ByteArrayOutputStream singleOutput = new ByteArrayOutputStream();
                    try (ZipOutputStream singleZip = new ZipOutputStream(singleOutput, StandardCharsets.UTF_8)) {
                        exportProblemToZip(problem, singleZip);
                    }
                    parentZip.putNextEntry(new ZipEntry(problem.getSlug() + ".zip"));
                    parentZip.write(singleOutput.toByteArray());
                    parentZip.closeEntry();
                }
            }
        }

        byte[] bytes = output.toByteArray();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }

    @PostMapping("/batch-visibility")
    public ApiResponse<Void> batchVisibility(@Valid @RequestBody BatchVisibilityRequest request) {
        if (request.ids() != null) {
            for (Long id : request.ids()) {
                adminService.updateProblemVisibility(id, request.visible());
            }
        }
        return ApiResponse.ok(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@Valid @RequestBody BatchDeleteRequest request) {
        if (request.ids() != null) {
            for (Long id : request.ids()) {
                problemService.deleteProblem(id);
            }
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/cases/{filename}")
    public ResponseEntity<byte[]> getTestCaseFile(
            @PathVariable("id") Long id,
            @PathVariable("filename") String filename
    ) {
        if (filename == null || !filename.matches("^[a-zA-Z0-9_\\-]+\\.(in|out|ans)$")) {
            throw new IllegalArgumentException("Invalid filename");
        }
        Path filePath = testCaseFileStorage.getCaseFilePath(id, filename);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] content = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void exportProblemToZip(Problem problem, ZipOutputStream zip) throws IOException {
        // 1. Write config.yml
        StringBuilder config = new StringBuilder();
        config.append("slug: ").append(problem.getSlug()).append("\n");
        config.append("title: ").append(problem.getTitle()).append("\n");
        config.append("difficulty: ").append(problem.getDifficulty() == null ? "Easy" : problem.getDifficulty()).append("\n");
        config.append("visible: ").append(problem.getVisible() != null && problem.getVisible()).append("\n");
        config.append("timeLimitMs: ").append(problem.getTimeLimitMs() == null ? 1000 : problem.getTimeLimitMs()).append("\n");
        config.append("memoryLimitKb: ").append(problem.getMemoryLimitKb() == null ? 262144 : problem.getMemoryLimitKb()).append("\n");

        if (problem.getTags() != null && !problem.getTags().isBlank()) {
            config.append("tags: ").append(problem.getTags()).append("\n");
        }

        List<TestCase> cases = problemService.testCases(problem.getId());

        // Extract sample cases
        List<String> samples = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        for (TestCase c : cases) {
            if (Boolean.TRUE.equals(c.getSample())) {
                samples.add(c.getCaseName());
            }
            scores.add(c.getCaseName() + ":" + (c.getScore() == null ? 0 : c.getScore()));
        }

        if (!samples.isEmpty()) {
            config.append("samples: ").append(String.join(",", samples)).append("\n");
        }
        if (!scores.isEmpty()) {
            config.append("scores: ").append(String.join(",", scores)).append("\n");
        }

        // Add config.yml entry
        zip.putNextEntry(new ZipEntry("config.yml"));
        zip.write(config.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        // 2. Write statement.md
        zip.putNextEntry(new ZipEntry("statement.md"));
        String description = problem.getDescription() == null ? "" : problem.getDescription();
        zip.write(description.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        // 3. Write cases
        for (TestCase c : cases) {
            if (c.getInputFile() != null && !c.getInputFile().isBlank()) {
                Path inputPath = testCaseFileStorage.getCaseFilePath(problem.getId(), c.getInputFile());
                if (Files.exists(inputPath)) {
                    zip.putNextEntry(new ZipEntry("cases/" + c.getInputFile()));
                    Files.copy(inputPath, zip);
                    zip.closeEntry();
                }
            }
            if (c.getOutputFile() != null && !c.getOutputFile().isBlank()) {
                Path outputPath = testCaseFileStorage.getCaseFilePath(problem.getId(), c.getOutputFile());
                if (Files.exists(outputPath)) {
                    zip.putNextEntry(new ZipEntry("cases/" + c.getOutputFile()));
                    Files.copy(outputPath, zip);
                    zip.closeEntry();
                }
            }
        }
    }

    private void addZipText(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public record AdminProblemDetail(Problem problem, List<TestCase> testCases) {}


    public record CreateProblemRequest(
            @NotBlank String slug,
            @NotBlank String title,
            @NotBlank String description,
            @Min(100) Integer timeLimitMs,
            @Min(16384) Integer memoryLimitKb,
            String difficulty,
            String tags,
            Boolean visible,
            @NotEmpty List<@Valid TestCaseRequest> testCases
    ) {
        ProblemService.CreateProblemCommand toCommand() {
            return new ProblemService.CreateProblemCommand(
                    slug,
                    title,
                    description,
                    timeLimitMs == null ? 1000 : timeLimitMs,
                    memoryLimitKb == null ? 262144 : memoryLimitKb,
                    difficulty == null || difficulty.isBlank() ? "Easy" : difficulty,
                    tags,
                    visible == null || visible,
                    testCases.stream().map(TestCaseRequest::toCommand).toList()
            );
        }
    }

    public record TestCaseRequest(
            String uploadToken,
            String name,
            String caseName,
            @NotBlank String inputFile,
            @NotBlank String outputFile,
            Long inputSize,
            Long outputSize,
            Integer score,
            Boolean sample
    ) {
        ProblemService.TestCaseCommand toCommand() {
            return new ProblemService.TestCaseCommand(
                    uploadToken,
                    resolvedName(),
                    inputFile,
                    outputFile,
                    inputSize,
                    outputSize,
                    score == null ? 100 : score,
                    sample
            );
        }

        private String resolvedName() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            return caseName;
        }
    }

    public record VisibilityRequest(@NotNull Boolean visible) {
    }

    public record BatchVisibilityRequest(
            @NotEmpty List<Long> ids,
            @NotNull Boolean visible
    ) {}

    public record BatchDeleteRequest(
            @NotEmpty List<Long> ids
    ) {}

}
