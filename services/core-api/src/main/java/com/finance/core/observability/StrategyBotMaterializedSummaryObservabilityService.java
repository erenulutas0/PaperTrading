package com.finance.core.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StrategyBotMaterializedSummaryObservabilityService {

    private final Duration refreshInterval;
    private final Duration activityWindow;
    private final int batchSize;
    private final LocalDateTime startedAt = LocalDateTime.now();
    private final AtomicLong scheduledTickCount = new AtomicLong(0);
    private final AtomicLong refreshSuccessCount = new AtomicLong(0);
    private final AtomicLong refreshFailureCount = new AtomicLong(0);
    private final AtomicInteger lastRefreshedBotCount = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastRefreshAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastSuccessAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastFailureAt = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    public StrategyBotMaterializedSummaryObservabilityService(
            @Value("${app.strategy-bots.summary-refresh-interval:PT15M}") Duration refreshInterval,
            @Value("${app.strategy-bots.summary-refresh-activity-window:PT168H}") Duration activityWindow,
            @Value("${app.strategy-bots.summary-refresh-batch-size:250}") int batchSize) {
        this.refreshInterval = refreshInterval == null ? Duration.ofMinutes(15) : refreshInterval;
        this.activityWindow = activityWindow == null ? Duration.ofDays(7) : activityWindow;
        this.batchSize = Math.max(batchSize, 1);
    }

    public void recordRefreshSuccess(int refreshedBotCount) {
        LocalDateTime now = LocalDateTime.now();
        scheduledTickCount.incrementAndGet();
        refreshSuccessCount.incrementAndGet();
        lastRefreshedBotCount.set(Math.max(refreshedBotCount, 0));
        lastRefreshAt.set(now);
        lastSuccessAt.set(now);
        lastError.set(null);
    }

    public void recordRefreshFailure(String error) {
        LocalDateTime now = LocalDateTime.now();
        scheduledTickCount.incrementAndGet();
        refreshFailureCount.incrementAndGet();
        lastRefreshedBotCount.set(0);
        lastRefreshAt.set(now);
        lastFailureAt.set(now);
        lastError.set(error);
    }

    public StrategyBotMaterializedSummarySchedulerSnapshot snapshot() {
        return new StrategyBotMaterializedSummarySchedulerSnapshot(
                startedAt,
                LocalDateTime.now(),
                refreshInterval.toSeconds(),
                activityWindow.toSeconds(),
                batchSize,
                scheduledTickCount.get(),
                refreshSuccessCount.get(),
                refreshFailureCount.get(),
                lastRefreshAt.get(),
                lastSuccessAt.get(),
                lastFailureAt.get(),
                lastRefreshedBotCount.get(),
                lastError.get()
        );
    }
}
