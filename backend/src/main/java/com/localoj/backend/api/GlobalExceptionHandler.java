package com.localoj.backend.api;

import com.localoj.backend.service.SystemLogService;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final SystemLogService systemLogService;

    public GlobalExceptionHandler(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ApiError> validation(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError("Invalid request"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError("Forbidden"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> serverError(Exception ex) {
        systemLogService.error("api", "internal_error", "后端接口发生未处理异常", null, null, null, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("Internal server error"));
    }
}
