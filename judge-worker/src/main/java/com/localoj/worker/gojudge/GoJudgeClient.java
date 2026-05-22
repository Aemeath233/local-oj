package com.localoj.worker.gojudge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GoJudgeClient {
    private final RestClient restClient;

    public GoJudgeClient(RestClient.Builder builder, @Value("${app.go-judge.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public List<GoJudgeResult> run(List<Map<String, Object>> commands) {
        GoJudgeResult[] results = restClient.post()
                .uri("/run")
                .body(Map.of("cmd", commands))
                .retrieve()
                .body(GoJudgeResult[].class);
        return results == null ? List.of() : List.of(results);
    }

    public void deleteFile(String fileId) {
        try {
            restClient.delete().uri("/file/{fileId}", fileId).retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // Cached files are best-effort cleanup. A failed cleanup must not hide a judge result.
        }
    }
}
