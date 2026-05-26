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
                    # A + B Problem

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

                    本压缩包是一个标准的题目包示例，用于快速在后台导入题目。

                    ## 目录与文件说明

                    1. **config.yml** (必须):
                       题目元数据配置文件。指定题目 Slug、标题、难度、标签、时间限制 (ms)、内存限制 (KB)、公开可见状态、分数分配方式以及充当样例展示的测试点名称。

                    2. **statement.md** (必须):
                       题面 Markdown 文档。支持标准的 Markdown 格式。
                       *注意*: 题面中不需要手动书写“样例”板块，系统会自动根据 `config.yml` 中配置的 `samples` 字段提取测试点内容并动态高亮渲染在前台“Samples”区域。

                    3. **cases/** 目录 (必须):
                       测试数据文件夹。包含成对的测试输入与输出文件。
                       * 每一个测试点需要有同名的输入文件 `.in` 和输出文件 `.out` 或 `.ans`（如 `1.in` 与 `1.out`）。

                    ## 数学公式书写规范

                    为了防止 Markdown 解析器与数学公式中的特殊字符发生冲突（如 `_` 误识别为斜体，`^` 误识别为上标，`<=` 或 `<` 误识别为 HTML 标签），本系统支持标准的 **LaTeX** 数学公式语法，并通过 KaTeX 引擎在前端进行渲染：

                    * **行内公式 (Inline Math)**: 使用单个 `$` 包裹。例如：`$ 1 \\le n \\le 100000 $` 会被渲染为漂亮的上标/下标和关系符号。
                      *注意*: 起始 `$` 后面不能紧跟空格，结束 `$` 前面不能紧贴空格。
                    * **块级公式 (Block Math)**: 使用双个 `$$` 包裹，独占一行或单独展示。例如：`$$ -10^9 \\le a_i \\le 10^9 $$`。
                    * **特殊字符书写**: 请**避免**直接在普通 Markdown 文本中书写含有 `_`、`^`、`<=` 或 `<` 的复杂算式，务必将此类公式用 `$` 或 `$$` 包裹起来，或者使用 LaTeX 的对应转义符（如 `\\le` 代表小于等于 `≤`，`\\ge` 代表大于等于 `≥`）。

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

}
