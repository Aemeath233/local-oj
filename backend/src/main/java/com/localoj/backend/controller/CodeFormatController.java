package com.localoj.backend.controller;

import com.localoj.backend.api.ApiResponse;
import com.localoj.backend.service.CodeFormatService;
import com.localoj.common.enums.Language;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/code")
public class CodeFormatController {
    private final CodeFormatService codeFormatService;

    public CodeFormatController(CodeFormatService codeFormatService) {
        this.codeFormatService = codeFormatService;
    }

    @PostMapping("/format")
    public ApiResponse<String> format(@Valid @RequestBody FormatRequest request) {
        String formatted = codeFormatService.format(request.language(), request.sourceCode());
        return ApiResponse.ok(formatted);
    }

    public record FormatRequest(
            @NotNull Language language,
            @NotBlank String sourceCode
    ) {
    }
}
