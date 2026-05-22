package com.localoj.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.security.CurrentUser;
import com.localoj.backend.security.SecurityUtils;
import com.localoj.backend.service.ContestService;
import com.localoj.common.mapper.ContestProblemMapper;
import com.localoj.common.model.Contest;
import com.localoj.common.model.ContestProblem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/contests")
public class AdminContestController {
    private final ContestService contestService;
    private final ContestProblemMapper contestProblemMapper;

    public AdminContestController(
            ContestService contestService,
            ContestProblemMapper contestProblemMapper
    ) {
        this.contestService = contestService;
        this.contestProblemMapper = contestProblemMapper;
    }

    @GetMapping
    public ApiResponse<List<Contest>> list() {
        CurrentUser user = SecurityUtils.currentUser();
        return ApiResponse.ok(contestService.listContests(user));
    }

    @PostMapping
    public ApiResponse<Contest> create(@Valid @RequestBody ContestRequest request) {
        return ApiResponse.ok(contestService.createContest(request.toCommand()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminContestDetail> getContest(@PathVariable("id") Long id) {
        CurrentUser user = SecurityUtils.currentUser();
        Contest contest = contestService.requireContest(id, user);
        List<ContestProblem> cpList = contestProblemMapper.selectList(new QueryWrapper<ContestProblem>()
                .eq("contest_id", id)
                .orderByAsc("sort_order"));
        List<Long> problemIds = cpList.stream().map(ContestProblem::getProblemId).toList();
        return ApiResponse.ok(new AdminContestDetail(contest, problemIds));
    }

    @PutMapping("/{id}")
    public ApiResponse<Contest> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody ContestRequest request
    ) {
        return ApiResponse.ok(contestService.updateContest(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Object> delete(@PathVariable("id") Long id) {
        contestService.deleteContest(id);
        return ApiResponse.ok(null);
    }

    public record AdminContestDetail(Contest contest, List<Long> problemIds) {
    }

    public record ContestRequest(
            @NotBlank String title,
            String description,
            @NotNull LocalDateTime startTime,
            @NotNull LocalDateTime endTime,
            @NotNull Boolean visible,
            String type,
            List<Long> problemIds
    ) {
        ContestService.ContestCommand toCommand() {
            return new ContestService.ContestCommand(
                    title,
                    description,
                    startTime,
                    endTime,
                    visible,
                    type != null ? type : "ACM",
                    problemIds
            );
        }
    }
}
