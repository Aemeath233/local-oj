package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.service.AdminDataService;
import com.coderushoj.backend.service.AdminUserImportService;
import com.coderushoj.backend.service.BackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/data")
public class AdminDataController {
    private final AdminDataService adminDataService;
    private final AdminUserImportService adminUserImportService;
    private final BackupService backupService;
    private final String dbUrl;
    private final String dbUsername;
    private final String mysqlPortMapping;

    public AdminDataController(
            AdminDataService adminDataService,
            AdminUserImportService adminUserImportService,
            BackupService backupService,
            @Value("${spring.datasource.url}") String dbUrl,
            @Value("${spring.datasource.username}") String dbUsername,
            @Value("${MYSQL_PORT:127.0.0.1:3307}") String mysqlPortMapping
    ) {
        this.adminDataService = adminDataService;
        this.adminUserImportService = adminUserImportService;
        this.backupService = backupService;
        this.dbUrl = dbUrl;
        this.dbUsername = dbUsername;
        this.mysqlPortMapping = mysqlPortMapping;
    }

    @PostMapping("/users/import")
    public ApiResponse<AdminUserImportService.ImportUserResult> importUsers(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        return ApiResponse.ok(adminUserImportService.importUsers(file));
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
        return ApiResponse.ok(adminDataService.getBackupInfo(dbUrl, dbUsername, mysqlPortMapping));
    }

    @GetMapping("/backup/export")
    public ResponseEntity<StreamingResponseBody> exportBackup() {
        String filename = "backup_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        StreamingResponseBody body = outputStream -> backupService.writeBackup(outputStream);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PostMapping("/backup/import")
    public ApiResponse<Void> importBackup(@RequestParam("file") MultipartFile file) throws IOException {
        backupService.importBackup(file);
        return ApiResponse.ok(null);
    }

    public record CleanupResponse(int count) {}
}
