package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.AdminService;
import com.localoj.backend.service.SubmissionService;
import com.localoj.common.model.Submission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/submissions")
public class AdminSubmissionController {
    private final AdminService adminService;
    private final SubmissionService submissionService;

    public AdminSubmissionController(AdminService adminService, SubmissionService submissionService) {
        this.adminService = adminService;
        this.submissionService = submissionService;
    }

    @GetMapping
    public ApiResponse<List<AdminService.SubmissionSummary>> list(
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        return ApiResponse.ok(adminService.recentSubmissions(limit));
    }

    @PostMapping("/{id}/rejudge")
    public ApiResponse<Submission> rejudge(@PathVariable("id") Long id) {
        return ApiResponse.ok(submissionService.rejudge(id));
    }

    @PostMapping("/requeue-unfinished")
    public ApiResponse<RequeueResult> requeueUnfinished() {
        return ApiResponse.ok(new RequeueResult(submissionService.requeueUnfinished()));
    }

    public record RequeueResult(int queued) {
    }
}
