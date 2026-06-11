package com.coderushoj.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.service.PlagiarismCheckService;
import com.coderushoj.common.mapper.ContestProblemMapper;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.model.ContestProblem;
import com.coderushoj.common.model.PlagiarismCheck;
import com.coderushoj.common.model.Problem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/plagiarism")
public class AdminPlagiarismController {

    private final PlagiarismCheckService plagiarismCheckService;
    private final ContestProblemMapper contestProblemMapper;
    private final ProblemMapper problemMapper;

    @Value("${app.jplag.workspace}")
    private String jplagWorkspace;

    public AdminPlagiarismController(
            PlagiarismCheckService plagiarismCheckService,
            ContestProblemMapper contestProblemMapper,
            ProblemMapper problemMapper
    ) {
        this.plagiarismCheckService = plagiarismCheckService;
        this.contestProblemMapper = contestProblemMapper;
        this.problemMapper = problemMapper;
    }

    @PostMapping("/check/{contestId}")
    public ApiResponse<String> triggerCheck(@PathVariable("contestId") Long contestId) {
        plagiarismCheckService.runPlagiarismCheckAsync(contestId);
        return ApiResponse.ok("查重任务已在后台拉起，请稍后刷新状态");
    }

    @GetMapping("/status/{contestId}")
    public ApiResponse<List<Map<String, Object>>> getStatus(@PathVariable("contestId") Long contestId) {
        // Fetch contest problems
        List<ContestProblem> cpList = contestProblemMapper.selectList(
                new LambdaQueryWrapper<ContestProblem>()
                        .eq(ContestProblem::getContestId, contestId)
                        .orderByAsc(ContestProblem::getSortOrder)
        );

        if (cpList.isEmpty()) {
            return ApiResponse.ok(Collections.emptyList());
        }

        List<Long> problemIds = cpList.stream().map(ContestProblem::getProblemId).toList();
        List<Problem> problems = problemMapper.selectBatchIds(problemIds);
        Map<Long, Problem> problemMap = problems.stream().collect(Collectors.toMap(Problem::getId, p -> p));

        // Fetch plagiarism checks
        List<PlagiarismCheck> checks = plagiarismCheckService.getChecksByContest(contestId);

        // Group checks by problem ID
        Map<Long, List<PlagiarismCheck>> checkMap = checks.stream()
                .collect(Collectors.groupingBy(PlagiarismCheck::getProblemId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < cpList.size(); i++) {
            ContestProblem cp = cpList.get(i);
            Problem problem = problemMap.get(cp.getProblemId());
            if (problem == null) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("problemId", problem.getId());
            row.put("problemTitle", problem.getTitle());
            row.put("problemSlug", problem.getSlug());
            row.put("sequence", getSequenceCode(i));

            List<PlagiarismCheck> problemChecks = checkMap.getOrDefault(problem.getId(), Collections.emptyList());
            List<Map<String, Object>> families = new ArrayList<>();
            for (PlagiarismCheck check : problemChecks) {
                Map<String, Object> fMap = new HashMap<>();
                fMap.put("family", check.getLanguageFamily());
                fMap.put("status", check.getStatus());
                fMap.put("maxSimilarity", check.getMaxSimilarity());
                fMap.put("errorMessage", check.getErrorMessage());
                fMap.put("updatedAt", check.getUpdatedAt());
                families.add(fMap);
            }
            row.put("checks", families);
            result.add(row);
        }

        return ApiResponse.ok(result);
    }

    @GetMapping("/report/{contestId}/{problemId}/{family}/**")
    public ResponseEntity<Resource> getReportFile(
            @PathVariable("contestId") Long contestId,
            @PathVariable("problemId") Long problemId,
            @PathVariable("family") String family,
            HttpServletRequest request
    ) {
        String prefix = "/api/admin/plagiarism/report/" + contestId + "/" + problemId + "/" + family + "/";
        String requestUri = request.getRequestURI();
        int index = requestUri.indexOf(prefix);
        
        String relativePath = "index.html";
        if (index != -1) {
            relativePath = requestUri.substring(index + prefix.length());
        }
        if (relativePath.isEmpty()) {
            relativePath = "index.html";
        }

        Path basePath = Path.of(jplagWorkspace, "contest_" + contestId, "problem_" + problemId, "report_" + family)
                .toAbsolutePath()
                .normalize();
        Path targetPath = basePath.resolve(relativePath).normalize();
        try {
            if (!targetPath.startsWith(basePath) || !Files.isRegularFile(targetPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Path realBasePath = basePath.toRealPath();
            Path realTargetPath = targetPath.toRealPath();
            if (!realTargetPath.startsWith(realBasePath) || !Files.isRegularFile(realTargetPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Resource resource = new FileSystemResource(realTargetPath);
            return ResponseEntity.ok()
                    .contentType(getMediaType(realTargetPath.getFileName().toString()))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MediaType getMediaType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".js")) return MediaType.parseMediaType("application/javascript");
        if (lower.endsWith(".css")) return MediaType.parseMediaType("text/css");
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".svg")) return MediaType.parseMediaType("image/svg+xml");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String getSequenceCode(int index) {
        StringBuilder code = new StringBuilder();
        int temp = index;
        while (temp >= 0) {
            code.insert(0, (char) (65 + (temp % 26)));
            temp = temp / 26 - 1;
        }
        return code.toString();
    }
}
