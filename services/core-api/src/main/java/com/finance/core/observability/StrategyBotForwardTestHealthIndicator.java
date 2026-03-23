package com.finance.core.observability;

import com.finance.core.config.StrategyBotForwardTestObservabilityProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component("strategyBotForwardTests")
public class StrategyBotForwardTestHealthIndicator implements HealthIndicator {

    private final StrategyBotForwardTestObservabilityService service;
    private final StrategyBotForwardTestObservabilityProperties properties;

    public StrategyBotForwardTestHealthIndicator(
            StrategyBotForwardTestObservabilityService service,
            StrategyBotForwardTestObservabilityProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Override
    public Health health() {
        StrategyBotForwardTestSchedulerSnapshot snapshot = service.snapshot();
        LocalDateTime now = snapshot.checkedAt();
        Duration staleThreshold = properties.normalizedStaleThreshold();
        long staleThresholdSeconds = staleThreshold.toSeconds();

        if (snapshot.lastTickAt() == null) {
            long startupAgeSeconds = snapshot.startedAt() == null ? 0 : Duration.between(snapshot.startedAt(), now).toSeconds();
            boolean awaitingFirstTick = startupAgeSeconds <= staleThresholdSeconds;
            Health.Builder builder = awaitingFirstTick ? Health.unknown() : Health.down();
            return builder
                    .withDetail("startedAt", detailValue(snapshot.startedAt()))
                    .withDetail("status", awaitingFirstTick ? "awaiting-first-tick" : "scheduler-not-ticking")
                    .withDetail("startupAgeSeconds", Math.max(startupAgeSeconds, 0))
                    .withDetail("staleThresholdSeconds", staleThresholdSeconds)
                    .withDetail("scheduledTickCount", snapshot.scheduledTickCount())
                    .withDetail("lastObservedRunningRunCount", snapshot.lastObservedRunningRunCount())
                    .build();
        }

        long lastTickAgeSeconds = Math.max(Duration.between(snapshot.lastTickAt(), now).toSeconds(), 0);
        boolean stale = lastTickAgeSeconds > staleThresholdSeconds;
        Health.Builder builder = stale ? Health.down() : Health.up();
        return builder
                .withDetail("startedAt", detailValue(snapshot.startedAt()))
                .withDetail("checkedAt", detailValue(snapshot.checkedAt()))
                .withDetail("staleThresholdSeconds", staleThresholdSeconds)
                .withDetail("lastTickAt", detailValue(snapshot.lastTickAt()))
                .withDetail("lastTickAgeSeconds", lastTickAgeSeconds)
                .withDetail("scheduledTickCount", snapshot.scheduledTickCount())
                .withDetail("lastObservedRunningRunCount", snapshot.lastObservedRunningRunCount())
                .withDetail("refreshAttemptCount", snapshot.refreshAttemptCount())
                .withDetail("refreshSuccessCount", snapshot.refreshSuccessCount())
                .withDetail("refreshFailureCount", snapshot.refreshFailureCount())
                .withDetail("refreshSkipCount", snapshot.refreshSkipCount())
                .withDetail("lastRefreshAt", detailValue(snapshot.lastRefreshAt()))
                .withDetail("lastSkipAt", detailValue(snapshot.lastSkipAt()))
                .withDetail("lastRefreshedRunId", detailValue(snapshot.lastRefreshedRunId()))
                .withDetail("lastRefreshedRunStatus", detailValue(snapshot.lastRefreshedRunStatus()))
                .withDetail("lastSkippedRunId", detailValue(snapshot.lastSkippedRunId()))
                .withDetail("lastSkipReason", detailValue(snapshot.lastSkipReason()))
                .withDetail("lastError", detailValue(snapshot.lastError()))
                .build();
    }

    private Object detailValue(Object value) {
        return value == null ? "" : value;
    }
}
