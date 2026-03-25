package com.finance.core.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component("strategyBotSummaries")
public class StrategyBotMaterializedSummaryHealthIndicator implements HealthIndicator {

    private final StrategyBotMaterializedSummaryObservabilityService service;
    private final Duration staleThreshold;

    public StrategyBotMaterializedSummaryHealthIndicator(
            StrategyBotMaterializedSummaryObservabilityService service,
            @Value("${app.strategy-bots.summary-refresh-stale-threshold:PT45M}") Duration staleThreshold) {
        this.service = service;
        this.staleThreshold = staleThreshold == null ? Duration.ofMinutes(45) : staleThreshold;
    }

    @Override
    public Health health() {
        StrategyBotMaterializedSummarySchedulerSnapshot snapshot = service.snapshot();
        if (snapshot.lastRefreshAt() == null) {
            long startupAgeSeconds = snapshot.startedAt() == null ? 0 : Math.max(Duration.between(snapshot.startedAt(), snapshot.checkedAt()).toSeconds(), 0);
            boolean awaitingFirstTick = startupAgeSeconds <= staleThreshold.toSeconds();
            Health.Builder builder = awaitingFirstTick ? Health.unknown() : Health.down();
            return builder
                    .withDetail("status", awaitingFirstTick ? "awaiting-first-refresh" : "summary-refresh-stale")
                    .withDetail("startupAgeSeconds", startupAgeSeconds)
                    .withDetail("staleThresholdSeconds", staleThreshold.toSeconds())
                    .withDetail("scheduledTickCount", snapshot.scheduledTickCount())
                    .build();
        }

        long lastRefreshAgeSeconds = Math.max(Duration.between(snapshot.lastRefreshAt(), snapshot.checkedAt()).toSeconds(), 0);
        Health.Builder builder = lastRefreshAgeSeconds > staleThreshold.toSeconds() ? Health.down() : Health.up();
        return builder
                .withDetail("checkedAt", snapshot.checkedAt())
                .withDetail("lastRefreshAt", snapshot.lastRefreshAt())
                .withDetail("lastRefreshAgeSeconds", lastRefreshAgeSeconds)
                .withDetail("staleThresholdSeconds", staleThreshold.toSeconds())
                .withDetail("scheduledTickCount", snapshot.scheduledTickCount())
                .withDetail("refreshSuccessCount", snapshot.refreshSuccessCount())
                .withDetail("refreshFailureCount", snapshot.refreshFailureCount())
                .withDetail("lastRefreshedBotCount", snapshot.lastRefreshedBotCount())
                .withDetail("lastError", snapshot.lastError() == null ? "" : snapshot.lastError())
                .build();
    }
}
