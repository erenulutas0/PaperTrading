package com.finance.core.observability;

import com.finance.core.domain.StrategyBotRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StrategyBotForwardTestObservabilityService {

    private final Duration refreshInterval;
    private final AtomicLong scheduledTickCount = new AtomicLong(0);
    private final AtomicInteger lastObservedRunningRunCount = new AtomicInteger(0);
    private final AtomicLong refreshAttemptCount = new AtomicLong(0);
    private final AtomicLong refreshSuccessCount = new AtomicLong(0);
    private final AtomicLong refreshFailureCount = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastTickAt = new AtomicReference<>(null);
    private final AtomicReference<LocalDateTime> lastRefreshAt = new AtomicReference<>(null);
    private final AtomicReference<UUID> lastRefreshedRunId = new AtomicReference<>(null);
    private final AtomicReference<String> lastRefreshedRunStatus = new AtomicReference<>(null);
    private final AtomicReference<String> lastError = new AtomicReference<>(null);

    public StrategyBotForwardTestObservabilityService(
            @Value("${app.strategy-bots.forward-test-refresh-interval:PT30S}") Duration refreshInterval) {
        this.refreshInterval = refreshInterval == null ? Duration.ofSeconds(30) : refreshInterval;
    }

    public void recordSchedulerTick(int runningRunCount) {
        scheduledTickCount.incrementAndGet();
        lastObservedRunningRunCount.set(Math.max(runningRunCount, 0));
        lastTickAt.set(LocalDateTime.now());
    }

    public void recordRefreshSuccess(UUID runId, StrategyBotRun.Status status) {
        refreshAttemptCount.incrementAndGet();
        refreshSuccessCount.incrementAndGet();
        lastRefreshAt.set(LocalDateTime.now());
        lastRefreshedRunId.set(runId);
        lastRefreshedRunStatus.set(status != null ? status.name() : null);
        lastError.set(null);
    }

    public void recordRefreshFailure(UUID runId, String error) {
        refreshAttemptCount.incrementAndGet();
        refreshFailureCount.incrementAndGet();
        lastRefreshAt.set(LocalDateTime.now());
        lastRefreshedRunId.set(runId);
        lastRefreshedRunStatus.set(StrategyBotRun.Status.FAILED.name());
        lastError.set(error);
    }

    public StrategyBotForwardTestSchedulerSnapshot snapshot() {
        return new StrategyBotForwardTestSchedulerSnapshot(
                LocalDateTime.now(),
                refreshInterval.toSeconds(),
                scheduledTickCount.get(),
                lastObservedRunningRunCount.get(),
                refreshAttemptCount.get(),
                refreshSuccessCount.get(),
                refreshFailureCount.get(),
                lastTickAt.get(),
                lastRefreshAt.get(),
                lastRefreshedRunId.get(),
                lastRefreshedRunStatus.get(),
                lastError.get()
        );
    }
}
