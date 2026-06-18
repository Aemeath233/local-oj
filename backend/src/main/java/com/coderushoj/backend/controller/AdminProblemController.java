package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.service.AdminService;
import com.coderushoj.backend.service.ProblemService;
import com.coderushoj.backend.service.ProblemPackageImportService;
import com.coderushoj.backend.service.ProblemPackageExportService;
import com.coderushoj.backend.service.TestCaseFileStorage;
import com.coderushoj.backend.service.TrainingService;
import com.coderushoj.common.model.TestCase;
import com.coderushoj.common.model.Problem;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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


@RestController
@RequestMapping("/api/admin/problems")
public class AdminProblemController {
    private final ProblemService problemService;
    private final AdminService adminService;
    private final TestCaseFileStorage testCaseFileStorage;
    private final ProblemPackageImportService problemPackageImportService;
    private final ProblemPackageExportService problemPackageExportService;
    private final TrainingService trainingService;

    public AdminProblemController(
            ProblemService problemService,
            AdminService adminService,
            TestCaseFileStorage testCaseFileStorage,
            ProblemPackageImportService problemPackageImportService,
            ProblemPackageExportService problemPackageExportService,
            TrainingService trainingService
    ) {
        this.problemService = problemService;
        this.adminService = adminService;
        this.testCaseFileStorage = testCaseFileStorage;
        this.problemPackageImportService = problemPackageImportService;
        this.problemPackageExportService = problemPackageExportService;
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
        byte[] packageBytes = problemPackageExportService.generateExamplePackage();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=coderush-oj-problem-package-example.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(packageBytes);
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportProblems(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要导出的题目");
        }

        List<Problem> problems = ids.stream()
                .map(problemService::requireProblem)
                .toList();

        String filename = ids.size() == 1 ? problems.get(0).getSlug() + ".zip" : "problems-export-" + System.currentTimeMillis() + ".zip";

        StreamingResponseBody body = outputStream -> problemPackageExportService.exportProblems(problems, outputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
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
            Integer score
    ) {
        ProblemService.TestCaseCommand toCommand() {
            return new ProblemService.TestCaseCommand(
                    uploadToken,
                    resolvedName(),
                    inputFile,
                    outputFile,
                    inputSize,
                    outputSize,
                    score == null ? 100 : score
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
