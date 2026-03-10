package com.finance.core.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiErrorResponses {

    private ApiErrorResponses() {
    }

    public static ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            Object details,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .header(RequestCorrelation.REQUEST_ID_HEADER, requestId(request))
                .body(new ApiErrorResponse(code, message, details, requestId(request)));
    }

    public static String requestId(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        Object value = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
