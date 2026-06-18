package com.coderushoj.backend.service;

import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.TestCase;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProblemPackageExportService {
    private final ProblemService problemService;
    private final TestCaseFileStorage testCaseFileStorage;

    public ProblemPackageExportService(ProblemService problemService, TestCaseFileStorage testCaseFileStorage) {
        this.problemService = problemService;
        this.testCaseFileStorage = testCaseFileStorage;
    }

    public void exportProblems(List<Problem> problems, OutputStream outputStream) throws IOException {
        if (problems.size() == 1) {
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                exportProblemToZip(problems.get(0), zip);
            }
        } else {
            try (ZipOutputStream parentZip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                for (Problem problem : problems) {
                    parentZip.putNextEntry(new ZipEntry(problem.getSlug() + ".zip"));
                    try (ZipOutputStream singleZip = new ZipOutputStream(new NonClosingOutputStream(parentZip), StandardCharsets.UTF_8)) {
                        exportProblemToZip(problem, singleZip);
                    }
                    parentZip.closeEntry();
                }
            }
        }
    }

    public byte[] generateExamplePackage() throws IOException {
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

                    ## Sample 1

                    :::sample
                    ```input
                    3
                    1 2 3
                    ```

                    ```output
                    6
                    ```
                    :::

                    ## Sample Explanation 1

                    The sum is $1 + 2 + 3 = 6$.

                    ## Sample 2

                    :::sample
                    ```input
                    5
                    -10 0 10 20 -5
                    ```

                    ```output
                    15
                    ```
                    :::

                    ## Sample Explanation 2

                    The sum is $-10 + 0 + 10 + 20 - 5 = 15$.

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
                    ├── config.yml          # 题目元数据、分数分配
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
                       - `scores` 可以写成 `case-name=score; other-case=score`，总分必须为 100。

                    2. `statement.md`
                       - 题面是一个 Markdown 文件。
                       - 不要在开头重复写一级标题 `# 标题`，系统会自动使用 `config.yml` 里的 `title`。
                       - 公开样例直接写在题面里，并使用 `:::sample` 包裹 `input` / `output` 代码块。

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
                    - 公开样例必须写在题面里，并使用 `:::sample` 包裹 `input` / `output` 代码块。
                    - 样例说明写在对应样例块后面，保持“样例、样例说明”的阅读顺序。
                    - 数学表达式使用 LaTeX，例如 `$1 \\le n \\le 10^5$`。

                    ## 配置要求

                    - `difficulty` 只能是 `Easy`、`Medium`、`Hard`。
                    - `timeLimitMs` 默认 `1000`，除非题目确实需要更高限制。
                    - `memoryLimitKb` 默认 `262144`。
                    - `tags` 使用简短、稳定的标签，不要创造太多近义标签。
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
                    - 检查题面中的公开样例也存在于 `cases/` 中，便于评测覆盖。
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
        return output.toByteArray();
    }

    private void exportProblemToZip(Problem problem, ZipOutputStream zip) throws IOException {
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
        List<String> scores = new ArrayList<>();
        for (TestCase c : cases) {
            scores.add(c.getCaseName() + ":" + (c.getScore() == null ? 0 : c.getScore()));
        }

        if (!scores.isEmpty()) {
            config.append("scores: ").append(String.join(",", scores)).append("\n");
        }

        zip.putNextEntry(new ZipEntry("config.yml"));
        zip.write(config.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

        zip.putNextEntry(new ZipEntry("statement.md"));
        String description = problem.getDescription() == null ? "" : problem.getDescription();
        zip.write(description.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();

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

    private static final class NonClosingOutputStream extends FilterOutputStream {
        private NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
