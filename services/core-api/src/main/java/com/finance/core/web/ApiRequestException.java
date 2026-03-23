package com.finance.core.web;

import org.springframework.http.HttpStatus;

public class ApiRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object details;
    private final boolean forceJson;

    private ApiRequestException(
            HttpStatus status,
            String code,
            String message,
            Object details,
            boolean forceJson) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
        this.forceJson = forceJson;
    }

    public static ApiRequestException badRequest(String code, String message) {
        return new ApiRequestException(HttpStatus.BAD_REQUEST, code, message, null, false);
    }

    public static ApiRequestException badRequestJson(String code, String message) {
        return new ApiRequestException(HttpStatus.BAD_REQUEST, code, message, null, true);
    }

    public static ApiRequestException unauthorized(String code, String message) {
        return new ApiRequestException(HttpStatus.UNAUTHORIZED, code, message, null, false);
    }

    public static ApiRequestException forbidden(String code, String message) {
        return new ApiRequestException(HttpStatus.FORBIDDEN, code, message, null, false);
    }

    public static ApiRequestException notFound(String code, String message) {
        return new ApiRequestException(HttpStatus.NOT_FOUND, code, message, null, false);
    }

    public static ApiRequestException conflict(String code, String message) {
        return new ApiRequestException(HttpStatus.CONFLICT, code, message, null, false);
    }

    public static ApiRequestException internal(String code, String message) {
        return new ApiRequestException(HttpStatus.INTERNAL_SERVER_ERROR, code, message, null, false);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Object details() {
        return details;
    }

    public boolean forceJson() {
        return forceJson;
    }
}
