package com.finance.core.controller;

import com.finance.core.security.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage() != null ? ex.getMessage() : "Invalid refresh token");
        problemDetail.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        problemDetail.setProperty("timestamp", LocalDateTime.now().toString());
        return problemDetail;
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        String msg = ex.getMessage();
        if (msg != null && (msg.toLowerCase().contains("not found"))) {
            status = HttpStatus.NOT_FOUND;
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, msg != null ? msg : "Unexpected error");
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("timestamp", LocalDateTime.now().toString());

        return problemDetail;
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problemDetail.setTitle("Bad Request");

        Map<String, String> errors = new java.util.HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", LocalDateTime.now().toString());

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal server error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", LocalDateTime.now().toString());

        // In production, we might want to log this carefully instead of exposing the
        // stack trace
        // problemDetail.setProperty("error", ex.getMessage());

        return problemDetail;
    }
}
