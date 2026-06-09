package com.coderushoj.backend.controller;

import com.coderushoj.backend.api.ApiResponse;
import com.coderushoj.backend.service.SystemVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemVersionController {
    private final SystemVersionService systemVersionService;

    public SystemVersionController(SystemVersionService systemVersionService) {
        this.systemVersionService = systemVersionService;
    }

    @GetMapping("/versions")
    public ApiResponse<Map<String, Long>> getVersions() {
        return ApiResponse.ok(systemVersionService.getVersions());
    }
}
