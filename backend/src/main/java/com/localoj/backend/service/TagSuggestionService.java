package com.localoj.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localoj.common.model.LlmSetting;
import com.localoj.common.model.ProblemTag;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TagSuggestionService {
    private final LlmSettingsService llmSettingsService;
    private final ProblemTagService problemTagService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TagSuggestionService(
            LlmSettingsService llmSettingsService,
            ProblemTagService problemTagService,
            ObjectMapper objectMapper
    ) {
        this.llmSettingsService = llmSettingsService;
        this.problemTagService = problemTagService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public TagSuggestionResult suggest(SuggestTagsCommand command) {
        List<String> candidates = problemTagService.list().stream()
                .map(ProblemTag::getName)
                .toList();
        if (candidates.isEmpty()) {
            return new TagSuggestionResult(List.of(), "LOCAL");
        }
        LlmSetting setting = llmSettingsService.requireSettings();
        if (Boolean.TRUE.equals(setting.getEnabled())
                && hasText(setting.getBaseUrl())
                && hasText(setting.getModel())) {
            return new TagSuggestionResult(suggestWithLlm(setting, candidates, command), "LLM");
        }
        return new TagSuggestionResult(suggestLocally(candidates, command), "LOCAL");
    }

    private List<String> suggestWithLlm(LlmSetting setting, List<String> candidates, SuggestTagsCommand command) {
        String prompt = """
                你是 Online Judge 题目标签助手。请只从候选标签中选择 1-5 个最合适的标签。
                必须返回 JSON，格式为 {"tags":["标签1","标签2"]}，不要返回解释。

                候选标签：%s
                难度：%s
                标题：%s
                题面：
                %s
                """.formatted(
                String.join("、", candidates),
                blankToEmpty(command.difficulty()),
                blankToEmpty(command.title()),
                blankToEmpty(command.description())
        );
        Map<String, Object> request = Map.of(
                "model", setting.getModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", "你只输出可解析 JSON。"),
                        Map.of("role", "user", "content", prompt)
                )
        );
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(chatCompletionsUrl(setting.getBaseUrl()))
                    .body(request);
            if (hasText(setting.getApiKey())) {
                spec = spec.header("Authorization", "Bearer " + setting.getApiKey());
            }
            String body = spec.retrieve().body(String.class);
            return filterCandidates(parseTagsFromResponse(body), candidates);
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("LLM 标签建议失败，请检查 Base URL、模型和密钥");
        }
    }

    private List<String> parseTagsFromResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return List.of();
            }
            JsonNode contentNode = objectMapper.readTree(stripCodeFence(content));
            JsonNode tagsNode = contentNode.path("tags");
            List<String> tags = new ArrayList<>();
            if (tagsNode.isArray()) {
                for (JsonNode tagNode : tagsNode) {
                    tags.add(tagNode.asText());
                }
            }
            return tags;
        } catch (Exception ex) {
            throw new IllegalArgumentException("LLM 返回格式无法解析");
        }
    }

    private List<String> suggestLocally(List<String> candidates, SuggestTagsCommand command) {
        String haystack = (blankToEmpty(command.title()) + "\n" + blankToEmpty(command.description()) + "\n" + blankToEmpty(command.difficulty()))
                .toLowerCase(Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>();
        for (String tag : candidates) {
            if (haystack.contains(tag.toLowerCase(Locale.ROOT))) {
                selected.add(tag);
            }
        }
        addByKeywords(selected, candidates, haystack, "动态规划", List.of("dp", "dynamic programming", "状态转移"));
        addByKeywords(selected, candidates, haystack, "图论", List.of("graph", "最短路", "路径", "连通", "拓扑", "dfs", "bfs"));
        addByKeywords(selected, candidates, haystack, "字符串", List.of("string", "子串", "回文", "匹配"));
        addByKeywords(selected, candidates, haystack, "数据结构", List.of("stack", "queue", "heap", "tree", "树", "栈", "队列", "堆"));
        addByKeywords(selected, candidates, haystack, "贪心", List.of("greedy", "最优", "排序后"));
        addByKeywords(selected, candidates, haystack, "数学", List.of("math", "gcd", "质数", "取模", "组合"));
        addByKeywords(selected, candidates, haystack, "模拟", List.of("simulate", "模拟"));
        addByKeywords(selected, candidates, haystack, "基础", List.of("入门", "基础", "easy"));
        if (selected.isEmpty() && candidates.contains("基础")) {
            selected.add("基础");
        }
        return selected.stream().limit(5).toList();
    }

    private void addByKeywords(Set<String> selected, List<String> candidates, String haystack, String tag, List<String> keywords) {
        if (!candidates.contains(tag)) {
            return;
        }
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                selected.add(tag);
                return;
            }
        }
    }

    private List<String> filterCandidates(List<String> tags, List<String> candidates) {
        Set<String> candidateSet = new LinkedHashSet<>(candidates);
        return tags.stream()
                .map(String::trim)
                .filter(candidateSet::contains)
                .distinct()
                .limit(5)
                .toList();
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record SuggestTagsCommand(String title, String description, String difficulty) {
    }

    public record TagSuggestionResult(List<String> tags, String source) {
    }
}
