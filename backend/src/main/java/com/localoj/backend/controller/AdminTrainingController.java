package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.TrainingService;
import com.localoj.common.model.Problem;
import com.localoj.common.model.TrainingSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/training")
public class AdminTrainingController {
    private final TrainingService trainingService;

    public AdminTrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @GetMapping
    public ApiResponse<List<TrainingService.TrainingSetDto>> list() {
        return ApiResponse.ok(trainingService.listTrainingSets(null, true));
    }

    @PostMapping
    public ApiResponse<TrainingSet> create(@Valid @RequestBody UpsertTrainingRequest request) {
        TrainingSet set = trainingService.createTrainingSet(
                request.title(),
                request.description(),
                request.visible() == null || request.visible()
        );
        return ApiResponse.ok(set);
    }

    @GetMapping("/{id}")
    public ApiResponse<TrainingSet> get(@PathVariable("id") Long id) {
        return ApiResponse.ok(trainingService.requireTrainingSet(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TrainingSet> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpsertTrainingRequest request
    ) {
        TrainingSet set = trainingService.updateTrainingSet(
                id,
                request.title(),
                request.description(),
                request.visible() == null || request.visible()
        );
        return ApiResponse.ok(set);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        trainingService.deleteTrainingSet(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/problems")
    public ApiResponse<List<Problem>> getProblems(@PathVariable("id") Long id) {
        return ApiResponse.ok(trainingService.getProblemsInSet(id, false));
    }

    @PostMapping("/{id}/link")
    public ApiResponse<Void> linkProblems(
            @PathVariable("id") Long id,
            @Valid @RequestBody LinkProblemsRequest request
    ) {
        trainingService.linkProblems(id, request.problemIds());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/unlink")
    public ApiResponse<Void> unlinkProblems(
            @PathVariable("id") Long id,
            @Valid @RequestBody LinkProblemsRequest request
    ) {
        trainingService.unlinkProblems(id, request.problemIds());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/reorder")
    public ApiResponse<Void> reorderProblems(
            @PathVariable("id") Long id,
            @Valid @RequestBody LinkProblemsRequest request
    ) {
        trainingService.reorderProblems(id, request.problemIds());
        return ApiResponse.ok(null);
    }

    public record UpsertTrainingRequest(
            @NotBlank String title,
            String description,
            Boolean visible
    ) {}

    public record LinkProblemsRequest(
            @NotEmpty List<Long> problemIds
    ) {}
}
