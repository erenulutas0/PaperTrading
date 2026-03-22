package com.finance.core.observability;

import java.time.LocalDateTime;
import java.util.UUID;

public record StrategyBotForwardTestSchedulerSnapshot(
        LocalDateTime checkedAt,
        long refreshIntervalSeconds,
        long scheduledTickCount,
        int lastObservedRunningRunCount,
        long refreshAttemptCount,
        long refreshSuccessCount,
        long refreshFailureCount,
        LocalDateTime lastTickAt,
        LocalDateTime lastRefreshAt,
        UUID lastRefreshedRunId,
        String lastRefreshedRunStatus,
        String lastError) {
}
