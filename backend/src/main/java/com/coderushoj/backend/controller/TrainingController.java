package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.backend.security.SecurityUtils;
import com.coderushoj.backend.service.TrainingService;
import com.coderushoj.common.model.TrainingSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/training")
public class TrainingController {
    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @GetMapping
    public ApiResponse<List<TrainingService.TrainingSetDto>> list() {
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(trainingService.listTrainingSets(user, false));
    }

    @GetMapping("/{id}")
    public ApiResponse<TrainingSet> detail(@PathVariable("id") Long id) {
        TrainingSet set = trainingService.requireTrainingSet(id);
        if (!Boolean.TRUE.equals(set.getVisible())) {
            throw new IllegalArgumentException("Training set not found");
        }
        return ApiResponse.ok(set);
    }

    @GetMapping("/{id}/problems")
    public ApiResponse<List<TrainingService.ProblemWithStatusDto>> listProblems(@PathVariable("id") Long id) {
        TrainingSet set = trainingService.requireTrainingSet(id);
        if (!Boolean.TRUE.equals(set.getVisible())) {
            throw new IllegalArgumentException("Training set not found");
        }
        CurrentUser user = SecurityUtils.optionalCurrentUser();
        return ApiResponse.ok(trainingService.getProblemsWithStatus(id, user));
    }
}
