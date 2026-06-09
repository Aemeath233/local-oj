package com.coderushoj.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.security.CurrentUser;
import com.coderushoj.backend.security.SecurityUtils;
import com.coderushoj.common.enums.Role;
import com.coderushoj.common.mapper.ProblemMapper;
import com.coderushoj.common.mapper.ProblemSolutionMapper;
import com.coderushoj.common.mapper.SubmissionMapper;
import com.coderushoj.common.mapper.UserMapper;
import com.coderushoj.common.model.Problem;
import com.coderushoj.common.model.ProblemSolution;
import com.coderushoj.common.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/problems/{problemId}/solutions")
public class ProblemSolutionController {
    private final ProblemSolutionMapper solutionMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;

    public ProblemSolutionController(
            ProblemSolutionMapper solutionMapper,
            SubmissionMapper submissionMapper,
            UserMapper userMapper,
            ProblemMapper problemMapper
    ) {
        this.solutionMapper = solutionMapper;
        this.submissionMapper = submissionMapper;
        this.userMapper = userMapper;
        this.problemMapper = problemMapper;
    }

    @GetMapping
    public ApiResponse<List<SolutionSummary>> list(
            @PathVariable("problemId") Long problemId
    ) {
        CurrentUser user = SecurityUtils.currentUser();
        ensurePassedProblem(user, problemId);

        List<ProblemSolution> list = solutionMapper.selectList(new QueryWrapper<ProblemSolution>()
                .eq("problem_id", problemId)
                .orderByDesc("updated_at"));

        List<SolutionSummary> summaries = list.stream().map(sol -> {
            User submitter = userMapper.selectById(sol.getUserId());
            return new SolutionSummary(
                    sol.getId(),
                    sol.getProblemId(),
                    sol.getUserId(),
                    submitter == null ? null : submitter.getUsername(),
                    submitter == null ? null : submitter.getDisplayName(),
                    submitter == null ? null : submitter.getAvatarUrl(),
                    sol.getTitle(),
                    sol.getCreatedAt(),
                    sol.getUpdatedAt()
            );
        }).toList();

        return ApiResponse.ok(summaries);
    }

    @GetMapping("/{solutionId}")
    public ApiResponse<SolutionDetail> detail(
            @PathVariable("problemId") Long problemId,
            @PathVariable("solutionId") Long solutionId
    ) {
        CurrentUser user = SecurityUtils.currentUser();
        ensurePassedProblem(user, problemId);

        ProblemSolution sol = solutionMapper.selectById(solutionId);
        if (sol == null || !sol.getProblemId().equals(problemId)) {
            throw new IllegalArgumentException("Solution not found");
        }

        User submitter = userMapper.selectById(sol.getUserId());
        return ApiResponse.ok(new SolutionDetail(
                sol.getId(),
                sol.getProblemId(),
                sol.getUserId(),
                submitter == null ? null : submitter.getUsername(),
                submitter == null ? null : submitter.getDisplayName(),
                submitter == null ? null : submitter.getAvatarUrl(),
                sol.getTitle(),
                sol.getContent(),
                sol.getCreatedAt(),
                sol.getUpdatedAt()
        ));
    }

    @PostMapping
    public ApiResponse<ProblemSolution> save(
            @PathVariable("problemId") Long problemId,
            @Valid @RequestBody SolutionSaveRequest request
    ) {
        CurrentUser user = SecurityUtils.currentUser();
        ensurePassedProblem(user, problemId);

        // Find if solution already exists for this user and problem
        ProblemSolution existing = solutionMapper.selectOne(new QueryWrapper<ProblemSolution>()
                .eq("problem_id", problemId)
                .eq("user_id", user.id()));

        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setTitle(request.title());
            existing.setContent(request.content());
            existing.setUpdatedAt(now);
            solutionMapper.updateById(existing);
            return ApiResponse.ok(existing);
        } else {
            ProblemSolution sol = new ProblemSolution();
            sol.setProblemId(problemId);
            sol.setUserId(user.id());
            sol.setTitle(request.title());
            sol.setContent(request.content());
            sol.setCreatedAt(now);
            sol.setUpdatedAt(now);
            solutionMapper.insert(sol);
            return ApiResponse.ok(sol);
        }
    }

    @DeleteMapping("/{solutionId}")
    public ApiResponse<Void> delete(
            @PathVariable("problemId") Long problemId,
            @PathVariable("solutionId") Long solutionId
    ) {
        CurrentUser user = SecurityUtils.currentUser();
        ProblemSolution sol = solutionMapper.selectById(solutionId);
        if (sol == null || !sol.getProblemId().equals(problemId)) {
            throw new IllegalArgumentException("Solution not found");
        }

        // Only author or Admin/Super Admin can delete
        if (user.role() != Role.ADMIN && user.role() != Role.SUPER_ADMIN && !sol.getUserId().equals(user.id())) {
            throw new IllegalArgumentException("您没有权限删除此题解！");
        }

        solutionMapper.deleteById(solutionId);
        return ApiResponse.ok(null);
    }

    private void ensurePassedProblem(CurrentUser user, Long problemId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new IllegalArgumentException("Problem not found");
        }
        // Admins can always view/write solutions
        if (user.role() == Role.ADMIN || user.role() == Role.SUPER_ADMIN) {
            return;
        }
        if (!Boolean.TRUE.equals(problem.getVisible())) {
            throw new IllegalArgumentException("Problem not found");
        }

        boolean hasPassed = submissionMapper.selectCount(new QueryWrapper<com.coderushoj.common.model.Submission>()
                .eq("user_id", user.id())
                .eq("problem_id", problemId)
                .isNull("contest_id")
                .eq("verdict", com.coderushoj.common.enums.Verdict.AC.name())) > 0;

        if (!hasPassed) {
            throw new IllegalArgumentException("您需要先通过（AC）该题目，才能查看或发布题解！");
        }
    }

    public record SolutionSaveRequest(@NotBlank String title, @NotBlank String content) {}

    public record SolutionSummary(
            Long id,
            Long problemId,
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record SolutionDetail(
            Long id,
            Long problemId,
            Long userId,
            String username,
            String displayName,
            String avatarUrl,
            String title,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
