package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.websocket.canary")
@Getter
@Setter
public class WebSocketCanaryProperties {

    private boolean enabled = true;
    private Duration interval = Duration.ofMinutes(2);
    private Duration connectTimeout = Duration.ofSeconds(8);
    private Duration messageTimeout = Duration.ofSeconds(8);
    private int warningConsecutiveFailureThreshold = 2;
    private int criticalFailureThreshold = 3;
    private int windowSize = 10;
    private int minWindowSamples = 5;
    private double warningFailureRatioThreshold = 0.40;
    private double criticalFailureRatioThreshold = 0.70;
    private int recoverySuccessThreshold = 2;
    private boolean alertOnRecovery = true;
    private String baseUrl = "http://localhost:8080";
    private String wsUrl = "";
    private String topicDestination = "/topic/ops/canary";
    private String userQueueDestination = "/queue/ops-canary";

    public Duration normalizedInterval() {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return Duration.ofMinutes(2);
        }
        return interval;
    }

    public Duration normalizedConnectTimeout() {
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            return Duration.ofSeconds(8);
        }
        return connectTimeout;
    }

    public Duration normalizedMessageTimeout() {
        if (messageTimeout == null || messageTimeout.isZero() || messageTimeout.isNegative()) {
            return Duration.ofSeconds(8);
        }
        return messageTimeout;
    }

    public int normalizedCriticalFailureThreshold() {
        return Math.max(1, criticalFailureThreshold);
    }

    public int normalizedWarningConsecutiveFailureThreshold() {
        int critical = normalizedCriticalFailureThreshold();
        return Math.max(1, Math.min(warningConsecutiveFailureThreshold, critical));
    }

    public int normalizedWindowSize() {
        return Math.max(3, windowSize);
    }

    public int normalizedMinWindowSamples() {
        int size = normalizedWindowSize();
        return Math.max(1, Math.min(minWindowSamples, size));
    }

    public double normalizedWarningFailureRatioThreshold() {
        double critical = normalizedCriticalFailureRatioThreshold();
        return clampRatio(Math.min(warningFailureRatioThreshold, critical));
    }

    public double normalizedCriticalFailureRatioThreshold() {
        return clampRatio(criticalFailureRatioThreshold);
    }

    public int normalizedRecoverySuccessThreshold() {
        return Math.max(1, recoverySuccessThreshold);
    }

    public String normalizedTopicDestination() {
        return normalizeDestination(topicDestination, "/topic/ops/canary");
    }

    public String normalizedUserQueueDestination() {
        return normalizeDestination(userQueueDestination, "/queue/ops-canary");
    }

    public String normalizedUserSubscribeDestination() {
        String queueDestination = normalizedUserQueueDestination();
        if (queueDestination.startsWith("/user/")) {
            return queueDestination;
        }
        return "/user" + queueDestination;
    }

    public String resolvedWsUrl() {
        if (StringUtils.hasText(wsUrl)) {
            return wsUrl.trim();
        }
        URI baseUri = URI.create(baseUrl);
        String wsScheme = "https".equalsIgnoreCase(baseUri.getScheme()) ? "wss" : "ws";
        String path = baseUri.getPath();
        String normalizedPath = (path == null || path.isBlank() || "/".equals(path))
                ? ""
                : path.replaceAll("/+$", "");
        return wsScheme + "://" + baseUri.getAuthority() + normalizedPath + "/ws";
    }

    private String normalizeDestination(String candidate, String fallback) {
        if (!StringUtils.hasText(candidate)) {
            return fallback;
        }
        String normalized = candidate.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }

    private double clampRatio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(value, 1.0));
    }
}
