package com.finance.core.web;

public record ApiErrorResponse(
        String code,
        String message,
        Object details,
        String requestId) {
}
