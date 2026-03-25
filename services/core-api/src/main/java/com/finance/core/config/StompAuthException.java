package com.finance.core.config;

public class StompAuthException extends IllegalArgumentException {

    private final String code;

    public StompAuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
