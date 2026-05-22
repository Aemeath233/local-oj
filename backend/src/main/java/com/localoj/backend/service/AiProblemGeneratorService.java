package com.localoj.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localoj.common.model.LlmSetting;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiProblemGeneratorService {
    private final LlmSettingsService llmSettingsService;
    private final TestCaseFileStorage testCaseFileStorage;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiProblemGeneratorService(
            LlmSettingsService llmSettingsService,
            TestCaseFileStorage testCaseFileStorage,
            ObjectMapper objectMapper
    ) {
        this.llmSettingsService = llmSettingsService;
        this.testCaseFileStorage = testCaseFileStorage;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public AiProblemResult generate(GenerateProblemCommand command) {
        LlmSetting setting = llmSettingsService.requireSettings();
        if (setting == null || !Boolean.TRUE.equals(setting.getEnabled())
                || setting.getBaseUrl() == null || setting.getBaseUrl().isBlank()
                || setting.getModel() == null || setting.getModel().isBlank()) {
            throw new IllegalArgumentException("请先在超级管理员后台的系统设置中启用并配置 LLM 服务（OpenAI-compatible Base URL, 密钥与模型名称）！");
        }

        String systemPrompt = "你是一个顶级的 Online Judge 题目出题专家和课程设计师。请严格按要求只输出可解析的合法 JSON 内容，绝不能包含任何其他 Markdown 标记、首尾包装或解释说明文字。";

        String userPrompt = """
                请将以下用户关于算法题目的输入整理成一篇高质量、结构完整且易读的标准 Online Judge 题目，并自动生成样例及测试点输入输出、和 Python 测试点生成脚本。

                用户输入：
                %s

                请输出一个合法且不含 Markdown 格式包装的 JSON 字符串，JSON 的结构必须严格匹配以下字段：
                {
                  "title": "题目中文标题",
                  "difficulty": "Easy" | "Medium" | "Hard" 的其中之一,
                  "timeLimitMs": 1000,
                  "memoryLimitKb": 262144,
                  "description": "# 题目描述\\n\\n给定一个升序整数数组...\\n\\n## 输入格式\\n\\n第一行包含...\\n\\n## 输出格式\\n\\n输出一个整数...\\n\\n## 数据范围\\n\\n- ...\\n\\n*注意：不要提供单独的输入/输出描述字段，所有的题目介绍、输入输出说明、约束与范围均需要在这一个 markdown 文档字段中呈现*",
                  "testCases": [
                    {
                      "inputText": "样例1输入文本",
                      "expectedOutput": "样例1期望输出文本",
                      "score": 50,
                      "sample": true
                    },
                    {
                      "inputText": "样例2输入文本",
                      "expectedOutput": "样例2期望输出文本",
                      "score": 50,
                      "sample": false
                    }
                  ],
                  "generatorScript": "# Python 测试点生成脚本，用于生成更大规模或随机的输入数据\\nimport random\\n..."
                }

                请只返回该合法 JSON，不要返回任何解释。
                """.formatted(command.prompt());

        Map<String, Object> request = Map.of(
                "model", setting.getModel(),
                "temperature", 0.2,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(chatCompletionsUrl(setting.getBaseUrl()))
                    .body(request);
            if (setting.getApiKey() != null && !setting.getApiKey().isBlank()) {
                spec = spec.header("Authorization", "Bearer " + setting.getApiKey());
            }
            String body = spec.retrieve().body(String.class);
            return parseResult(body);
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("LLM 服务请求失败，请确认系统设置中的 API 地址、模型及密钥是否配置正确：" + ex.getMessage());
        }
    }

    private AiProblemResult parseResult(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalArgumentException("LLM 返回内容为空");
            }
            content = stripCodeFence(content);
            JsonNode contentNode = objectMapper.readTree(content);

            String title = contentNode.path("title").asText("未命名题目");
            String difficulty = contentNode.path("difficulty").asText("Easy");
            int timeLimitMs = contentNode.path("timeLimitMs").asInt(1000);
            int memoryLimitKb = contentNode.path("memoryLimitKb").asInt(262144);
            String description = contentNode.path("description").asText("");
            String generatorScript = contentNode.path("generatorScript").asText("");

            List<TestCaseFileStorage.TestCaseText> testCaseTexts = new ArrayList<>();
            JsonNode casesNode = contentNode.path("testCases");
            if (casesNode.isArray()) {
                for (JsonNode caseNode : casesNode) {
                    String inText = caseNode.path("inputText").asText("");
                    String outText = caseNode.path("expectedOutput").asText("");
                    int score = caseNode.path("score").asInt(10);
                    boolean sample = caseNode.path("sample").asBoolean(false);
                    testCaseTexts.add(new TestCaseFileStorage.TestCaseText(inText, outText, score, sample));
                }
            }

            List<TestCaseFileStorage.ImportedCase> cases = testCaseFileStorage.saveTextCases(testCaseTexts);

            return new AiProblemResult(
                    title,
                    difficulty,
                    timeLimitMs,
                    memoryLimitKb,
                    description,
                    cases,
                    generatorScript
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("解析 AI 生成题目 JSON 格式失败，请重试或微调输入：" + ex.getMessage());
        }
    }

    private String chatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return trimmed;
    }

    public record GenerateProblemCommand(String prompt) {}

    public record AiProblemResult(
            String title,
            String difficulty,
            int timeLimitMs,
            int memoryLimitKb,
            String description,
            List<TestCaseFileStorage.ImportedCase> testCases,
            String generatorScript
    ) {}
}
