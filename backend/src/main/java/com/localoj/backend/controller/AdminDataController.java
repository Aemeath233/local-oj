package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.AdminDataService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/data")
public class AdminDataController {
    private final AdminDataService adminDataService;
    private final String dbUrl;
    private final String dbUsername;

    public AdminDataController(
            AdminDataService adminDataService,
            @Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername
    ) {
        this.adminDataService = adminDataService;
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
    }

    @PostMapping("/users/import")
    public ApiResponse<AdminDataService.ImportUserResult> importUsers(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(adminDataService.importUsers(file));
    }

    @PostMapping("/submissions/cleanup")
    public ApiResponse<CleanupResponse> cleanupSubmissions(
            @RequestBody AdminDataService.CleanupRequest request
    ) {
        int count = adminDataService.cleanupSubmissions(request);
        return ApiResponse.ok(new CleanupResponse(count));
    }

    @GetMapping("/test-cases/stats")
    public ApiResponse<AdminDataService.StorageStats> getStorageStats() throws IOException {
        return ApiResponse.ok(adminDataService.getStorageStats());
    }

    @DeleteMapping("/test-cases/orphaned")
    public ApiResponse<CleanupResponse> cleanOrphanedDirectories() throws IOException {
        int count = adminDataService.cleanOrphanedDirectories();
        return ApiResponse.ok(new CleanupResponse(count));
    }

    @GetMapping("/backup/info")
    public ApiResponse<AdminDataService.BackupInfo> getBackupInfo() {
        return ApiResponse.ok(adminDataService.getBackupInfo(dbUrl, dbUsername));
    }

    public record CleanupResponse(int count) {}
}
