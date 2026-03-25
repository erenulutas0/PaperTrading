package com.finance.core.observability;

import java.time.LocalDateTime;

public record StrategyBotMaterializedSummarySchedulerSnapshot(
        LocalDateTime startedAt,
        LocalDateTime checkedAt,
        long refreshIntervalSeconds,
        long activityWindowSeconds,
        int batchSize,
        long scheduledTickCount,
        long refreshSuccessCount,
        long refreshFailureCount,
        LocalDateTime lastRefreshAt,
        LocalDateTime lastSuccessAt,
        LocalDateTime lastFailureAt,
        int lastRefreshedBotCount,
        String lastError) {
}
