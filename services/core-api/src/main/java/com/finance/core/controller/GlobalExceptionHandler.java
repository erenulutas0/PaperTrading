package com.finance.core.controller;

import com.finance.core.security.InvalidRefreshTokenException;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRefreshToken(
            InvalidRefreshTokenException ex,
            HttpServletRequest request) {
        return ApiErrorResponses.build(
                HttpStatus.UNAUTHORIZED,
                "invalid_refresh_token",
                ex.getMessage() != null ? ex.getMessage() : "Invalid refresh token",
                null,
                request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(
            RuntimeException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String code = "bad_request";

        String msg = ex.getMessage();
        if (msg != null && (msg.toLowerCase().contains("not found"))) {
            status = HttpStatus.NOT_FOUND;
            code = "not_found";
        }
        return ApiErrorResponses.build(status, code, msg != null ? msg : "Unexpected error", null, request);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            org.springframework.web.bind.MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> errors = new java.util.HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Validation failed",
                errors,
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        return ApiErrorResponses.build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal_error",
                "An internal server error occurred",
                null,
                request);
    }
}
