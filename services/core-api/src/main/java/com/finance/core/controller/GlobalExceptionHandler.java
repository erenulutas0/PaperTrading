package com.finance.core.controller;

import com.finance.core.security.InvalidRefreshTokenException;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = switch (status) {
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not_found";
            case BAD_REQUEST -> "bad_request";
            default -> status.name().toLowerCase();
        };
        String message = ex.getReason() != null && !ex.getReason().isBlank()
                ? ex.getReason()
                : status.getReasonPhrase();
        return ApiErrorResponses.build(status, code, message, null, request);
    }

    @ExceptionHandler(ApiRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleApiRequestException(
            ApiRequestException ex,
            HttpServletRequest request) {
        if (ex.forceJson()) {
            return ApiErrorResponses.buildJson(ex.status(), ex.code(), ex.getMessage(), ex.details(), request);
        }
        return ApiErrorResponses.build(ex.status(), ex.code(), ex.getMessage(), ex.details(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleRuntime(
            RuntimeException ex,
            HttpServletRequest request) {
        String msg = ex.getMessage();
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                "bad_request",
                msg != null ? msg : "Unexpected error",
                null,
                request);
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
