package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.service.ProblemTagService;
import com.coderushoj.common.model.ProblemTag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tags")
public class AdminTagController {
    private final ProblemTagService problemTagService;

    public AdminTagController(ProblemTagService problemTagService) {
        this.problemTagService = problemTagService;
    }

    @GetMapping
    public ApiResponse<List<ProblemTag>> list() {
        return ApiResponse.ok(problemTagService.list());
    }

    @PostMapping
    public ApiResponse<ProblemTag> create(@Valid @RequestBody ProblemTagRequest request) {
        return ApiResponse.ok(problemTagService.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProblemTag> update(@PathVariable("id") Long id, @Valid @RequestBody ProblemTagRequest request) {
        return ApiResponse.ok(problemTagService.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable("id") Long id) {
        problemTagService.delete(id);
        return ApiResponse.ok(null);
    }

    public record ProblemTagRequest(@NotBlank String name, String color) {
        ProblemTagService.ProblemTagCommand toCommand() {
            return new ProblemTagService.ProblemTagCommand(name, color);
        }
    }
}
