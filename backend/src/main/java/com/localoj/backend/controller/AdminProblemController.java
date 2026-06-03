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
        validateTestCaseScores(request);
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
        validateTestCaseScores(request);
        problemService.updateProblem(id, request.toCommand());
        return ApiResponse.ok(detail(id));
    }

    private void validateTestCaseScores(CreateProblemRequest request) {
        if (request.testCases() == null || request.testCases().isEmpty()) {
            throw new IllegalArgumentException("测试点不能为空");
        }
        int totalScore = 0;
        for (TestCaseRequest tc : request.testCases()) {
            Integer score = tc.score();
            if (score == null) {
                score = 100;
            }
            if (score < 0 || score > 100) {
                throw new IllegalArgumentException("测试点分值必须在 0 到 100 之间");
            }
            totalScore += score;
        }
        if (totalScore != 100) {
            throw new IllegalArgumentException("所有测试点的分值总和必须等于 100");
        }
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
                    slug: list-sum-template
                    title: Integer List Sum Template
                    difficulty: Easy
                    tags: [基础, 输入输出, 测试设计]
                    timeLimitMs: 1000
                    memoryLimitKb: 262144
                    visible: true
                    samples: [sample-1, sample-2]
                    scores:
                      sample-1: 5
                      sample-2: 5
                      min-n: 9
                      single-negative: 9
                      all-zero: 9
                      mixed-sign: 9
                      max-value-pair: 9
                      max-n-pattern: 9
                      alternating: 9
                      random-small-01: 9
                      random-small-02: 9
                      random-medium-01: 9
                    """);
            addZipText(zip, "statement.md", """
                    Given a list of integers, output their sum.

                    ## Input

                    The first line contains an integer $n$.

                    The second line contains $n$ integers $a_1, a_2, \\ldots, a_n$.

                    ## Output

                    Output one integer, the sum of all numbers in the list.

                    ## Constraints

                    $1 \\le n \\le 50$

                    $-10^9 \\le a_i \\le 10^9$
                    """);
            addZipText(zip, "README.md", """
                    # CodeRush OJ 题目包示例

                    这个 ZIP 是后台“导入题目”功能的标准示例包，也可以作为交给 LLM/Agent 出题的模板。
                    下次需要生成新题时，可以直接把整个 ZIP 发给 Agent，让它阅读根目录下的 `AGENTS.md`，然后替换 `config.yml`、`statement.md` 和 `cases/` 中的测试数据。

                    ## 文件结构

                    ```text
                    problem-package.zip
                    ├── config.yml          # 题目元数据、样例测试点、分数分配
                    ├── statement.md        # 单文件 Markdown 题面
                    ├── README.md           # 给人的说明，导入时会被忽略
                    ├── AGENTS.md           # 给 LLM/Agent 的出题说明，导入时会被忽略
                    └── cases/
                        ├── sample-1.in
                        ├── sample-1.out
                        └── ...
                    ```

                    ## 必须文件

                    1. `config.yml`
                       - `difficulty` 只能使用 `Easy`、`Medium`、`Hard`。
                       - `timeLimitMs` 建议默认 `1000`，最低 `100`。
                       - `memoryLimitKb` 建议默认 `262144`，最低 `16384`。
                       - `samples` 填写要展示给用户的测试点 basename。
                       - `scores` 可以写成 `case-name=score; other-case=score`，总分必须为 100。

                    2. `statement.md`
                       - 题面是一个 Markdown 文件。
                       - 不要在开头重复写一级标题 `# 标题`，系统会自动使用 `config.yml` 里的 `title`。
                       - 不需要手写“样例输入/输出”板块，系统会根据 `samples` 自动展示样例。

                    3. `cases/`
                       - 每个测试点必须有同名的 `.in` 输入文件和 `.out` 或 `.ans` 输出文件。
                       - 例如 `sample-1.in` 必须搭配 `sample-1.out` 或 `sample-1.ans`。

                    ## 本示例的测试点设计

                    这个示例题非常简单，重点不是题目本身，而是展示测试数据应该如何设计：

                    - `sample-*`：公开样例，短小、可读。
                    - `min-n`：最小规模。
                    - `single-negative`：单个负数和下界数值。
                    - `all-zero`：全 0 特殊情况。
                    - `mixed-sign`：正负混合并互相抵消。
                    - `max-value-pair`：接近数值边界。
                    - `max-n-pattern`：达到本题最大 `n`。
                    - `alternating`：规律性强、容易暴露循环或下标问题。
                    - `random-*`：固定内容的随机风格测试点，用于补充覆盖。

                    真正生成新题时，建议每道题至少准备：2 组公开样例、若干边界/退化/构造测试点、若干固定种子的随机测试点，并保证输出由参考解校验。

                    ## 数学公式书写规范 (LaTeX Math Syntax)

                    本系统支持标准 LaTeX 公式语法，并通过 KaTeX 在前端渲染：

                    - 行内公式使用单个 `$`，例如 `$1 \\le n \\le 50$`。
                    - 块级公式使用 `$$`。
                    - 避免直接在普通 Markdown 文本中书写含有 `_`、`^`、`<=`、`<` 的复杂算式，优先放进公式环境。

                    ## 导入机制说明

                    导入过程中，除 `config.yml`、`statement.md` 和 `cases/` 下的成对 `.in`/`.out`/`.ans` 测试点文件外，`README.md`、`AGENTS.md` 或其他辅助文件都会被后端服务自动过滤，不会保存到题目数据目录。
                    """);
            addZipText(zip, "AGENTS.md", """
                    # Instructions for LLM Problem Authors

                    你是为 CodeRush OJ 生成题目包的出题 Agent。请把这个 ZIP 当作模板，生成一份可以直接导入的单题 ZIP。

                    ## 输出目标

                    - 保留标准结构：`config.yml`、`statement.md`、`cases/*.in`、`cases/*.out` 或 `cases/*.ans`。
                    - 可以保留或更新 `README.md` 和 `AGENTS.md`，但它们只是辅助说明，OJ 导入时会忽略。
                    - 不要把所有文件再包进一层多余目录，ZIP 根目录应该直接包含 `config.yml`。
                    - 不要生成需要人工粘贴输入/输出的大段说明，测试数据必须落在 `cases/` 文件中。

                    ## 题面要求

                    - `statement.md` 使用一个完整的 Markdown 文档描述题目。
                    - 开头不要写一级标题，系统会用 `config.yml` 的 `title` 渲染标题。
                    - 写清楚题意、输入格式、输出格式、约束、说明。
                    - 样例不必手写在题面里，公开样例由 `config.yml` 的 `samples` 指定。
                    - 数学表达式使用 LaTeX，例如 `$1 \\le n \\le 10^5$`。

                    ## 配置要求

                    - `difficulty` 只能是 `Easy`、`Medium`、`Hard`。
                    - `timeLimitMs` 默认 `1000`，除非题目确实需要更高限制。
                    - `memoryLimitKb` 默认 `262144`。
                    - `tags` 使用简短、稳定的标签，不要创造太多近义标签。
                    - `samples` 只放公开样例测试点，通常 1 到 2 组。
                    - `scores` 显式写出全部测试点分数，总分必须等于 100。

                    ## 测试数据设计要求

                    请按软件测试思想设计测试点，不能只给几组随手写的数据：

                    - 等价类：覆盖每类主要输入形态。
                    - 边界值：覆盖最小规模、最大规模、最小值、最大值、刚好相等、刚好越过分支边界前后的合法值。
                    - 退化情况：空结构的合法替代形态、单元素、全相同、全 0、只有一种字符或一种边。
                    - 构造性数据：单调递增、单调递减、交替、重复值很多、答案为 0、答案很大、存在多个最优解。
                    - 对抗数据：专门卡常见错误算法、溢出风险、下标边界、排序稳定性、贪心误判或动态规划初始化错误。
                    - 随机数据：使用固定 seed 生成小、中、大规模随机测试点，并在 README 中说明 seed 或生成策略。
                    - 样例数据：必须短小、可手算、能解释题意；隐藏测试点才负责强覆盖。

                    推荐每题至少包含：

                    - 2 组公开样例。
                    - 4 到 8 组边界/退化/构造测试点。
                    - 4 到 10 组固定 seed 的随机测试点。
                    - 对 Hard 题或容易被错误算法骗过的题，额外加入针对性反例。

                    ## 正确性检查

                    - 所有 `.in` 和 `.out`/`.ans` 必须一一配对，basename 完全一致。
                    - 所有输出必须由参考解实际计算，不要靠猜。
                    - 检查每个输入都满足题面约束。
                    - 检查 `scores` 引用的测试点都真实存在。
                    - 检查公开样例 basename 都写进了 `samples`。
                    - 检查所有测试点分数总和为 100。

                    ## 命名建议

                    使用能表达测试意图的 basename，例如：

                    - `sample-1`
                    - `min-n`
                    - `max-n`
                    - `all-zero`
                    - `single-element`
                    - `sorted-ascending`
                    - `adversarial-greedy`
                    - `random-small-01`
                    - `random-medium-01`
                    - `random-large-01`
                    """);
            addExampleCase(zip, "sample-1", "3\n1 2 3\n", "6\n");
            addExampleCase(zip, "sample-2", "5\n-10 0 10 20 -5\n", "15\n");
            addExampleCase(zip, "min-n", "1\n0\n", "0\n");
            addExampleCase(zip, "single-negative", "1\n-1000000000\n", "-1000000000\n");
            addExampleCase(zip, "all-zero", "8\n0 0 0 0 0 0 0 0\n", "0\n");
            addExampleCase(zip, "mixed-sign", "10\n-5 7 -3 2 -1 0 4 -2 6 -8\n", "0\n");
            addExampleCase(zip, "max-value-pair", "4\n1000000000 1000000000 -1000000000 -1000000000\n", "0\n");
            addExampleCase(zip, "max-n-pattern", """
                    50
                    1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50
                    """, "1275\n");
            addExampleCase(zip, "alternating", "20\n1 -1 1 -1 1 -1 1 -1 1 -1 1 -1 1 -1 1 -1 1 -1 1 -1\n", "0\n");
            addExampleCase(zip, "random-small-01", "7\n13 -4 0 8 -15 23 -2\n", "23\n");
            addExampleCase(zip, "random-small-02", "9\n-100 57 42 -13 0 19 -7 88 -86\n", "0\n");
            addExampleCase(zip, "random-medium-01", """
                    30
                    91 -17 42 0 -300 118 76 -45 230 -12 5 -8 19 -64 128 -256 512 -1024 2048 -4096 7 11 -13 29 -31 37 -41 43 -47 53
                    """, "-2505\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=coderush-oj-problem-package-example.zip")
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

    private void addExampleCase(ZipOutputStream zip, String basename, String input, String output) throws IOException {
        addZipText(zip, "cases/" + basename + ".in", input);
        addZipText(zip, "cases/" + basename + ".out", output);
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
