package com.finance.core.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth.jwt")
@Getter
@Setter
public class JwtRuntimeProperties {

    private String secret = "dev-only-change-me-with-at-least-32-bytes-for-hs256";
    private String issuer = "finance-core-api";
    private Duration accessTokenTtl = Duration.ofHours(12);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private Duration notificationStreamTokenTtl = Duration.ofMinutes(2);

    /**
     * Transition flag:
     * when true, requests can still authenticate with legacy X-User-Id header.
     */
    private boolean allowLegacyUserIdHeader = true;

    /**
     * When both Bearer token and X-User-Id are provided, enforce identity consistency.
     */
    private boolean enforceHeaderTokenMatch = true;

    public Duration normalizedAccessTokenTtl() {
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            return Duration.ofHours(12);
        }
        return accessTokenTtl;
    }

    public Duration normalizedRefreshTokenTtl() {
        if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            return Duration.ofDays(30);
        }
        return refreshTokenTtl;
    }

    public Duration normalizedNotificationStreamTokenTtl() {
        if (notificationStreamTokenTtl == null || notificationStreamTokenTtl.isZero() || notificationStreamTokenTtl.isNegative()) {
            return Duration.ofMinutes(2);
        }
        return notificationStreamTokenTtl;
    }
}
