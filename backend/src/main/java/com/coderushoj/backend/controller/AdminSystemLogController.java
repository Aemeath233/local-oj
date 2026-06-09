package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.service.SystemLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminSystemLogController {
    private final SystemLogService systemLogService;

    @Value("${app.data-root}")
    private String dataRoot;

    public AdminSystemLogController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @GetMapping("/toggle")
    public ApiResponse<Map<String, Boolean>> getToggle() {
        return ApiResponse.ok(Map.of("enabled", systemLogService.isLoggingEnabled()));
    }

    @PostMapping("/toggle")
    public ApiResponse<Void> updateToggle(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled != null) {
            systemLogService.setLoggingEnabled(enabled);
        }
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/clear")
    public ApiResponse<Void> clear(@RequestParam("type") String type) {
        File logFile;
        if ("worker".equalsIgnoreCase(type)) {
            logFile = new File(dataRoot, "logs/judge-worker.log");
        } else {
            logFile = new File(dataRoot, "logs/backend.log");
        }
        if (logFile.exists()) {
            try (java.io.FileWriter writer = new java.io.FileWriter(logFile, false)) {
                writer.write("");
            } catch (IOException e) {
                throw new IllegalArgumentException("无法清空日志文件: " + e.getMessage());
            }
        }
        return ApiResponse.ok(null);
    }

    @GetMapping("/download")
    public void download(
            @RequestParam("type") String type,
            HttpServletResponse response
    ) throws IOException {
        File logFile;
        if ("worker".equalsIgnoreCase(type)) {
            logFile = new File(dataRoot, "logs/judge-worker.log");
        } else {
            logFile = new File(dataRoot, "logs/backend.log");
        }

        if (!logFile.exists()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/plain; charset=utf-8");
            response.getWriter().write("日志文件尚不存在 (Log file does not exist yet)");
            return;
        }

        response.setContentType("text/plain; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + logFile.getName() + "\"");
        response.setContentLengthLong(logFile.length());

        try (FileInputStream in = new FileInputStream(logFile);
             OutputStream out = response.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
