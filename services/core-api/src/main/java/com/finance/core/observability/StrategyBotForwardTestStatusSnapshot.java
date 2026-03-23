package com.finance.core.observability;

import java.time.LocalDateTime;
import java.util.UUID;

public record StrategyBotForwardTestStatusSnapshot(
        LocalDateTime startedAt,
        LocalDateTime checkedAt,
        long refreshIntervalSeconds,
        long staleThresholdSeconds,
        String alertState,
        double lastTickAgeSeconds,
        long scheduledTickCount,
        int lastObservedRunningRunCount,
        long refreshAttemptCount,
        long refreshSuccessCount,
        long refreshFailureCount,
        long refreshSkipCount,
        LocalDateTime lastTickAt,
        LocalDateTime lastRefreshAt,
        LocalDateTime lastSkipAt,
        UUID lastRefreshedRunId,
        String lastRefreshedRunStatus,
        UUID lastSkippedRunId,
        String lastSkipReason,
        String lastError) {
}
