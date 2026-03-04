package com.finance.core.security;

public record AuthSessionTokens(
        String accessToken,
        String refreshToken,
        long accessExpiresInSeconds,
        long refreshExpiresInSeconds) {
}
