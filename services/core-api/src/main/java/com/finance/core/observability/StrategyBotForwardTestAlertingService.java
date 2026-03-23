package com.finance.core.observability;

import com.finance.core.config.StrategyBotForwardTestObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyBotForwardTestAlertingService {

    private static final String COMPONENT = "strategy-bot-forward-test-scheduler";
    private static final String ALERT_KEY = "scheduler-stale";
    private static final String RECOVERY_ALERT_KEY = "scheduler-recovered";

    private final StrategyBotForwardTestObservabilityService observabilityService;
    private final StrategyBotForwardTestObservabilityProperties properties;
    private final MeterRegistry meterRegistry;
    private final OpsAlertPublisher opsAlertPublisher;

    private final AtomicInteger alertStateGauge = new AtomicInteger(0);
    private final AtomicReference<Double> lastTickAgeSecondsGauge = new AtomicReference<>(0.0);
    private final AtomicReference<AlertState> alertState = new AtomicReference<>(AlertState.NONE);

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge("app.strategy.bot.forward_test_scheduler.alert.state", alertStateGauge);
        meterRegistry.gauge("app.strategy.bot.forward_test_scheduler.last_tick_age.seconds", lastTickAgeSecondsGauge, AtomicReference::get);
    }

    @Scheduled(fixedDelayString = "${app.strategy-bots.forward-test-observability.refresh-interval:PT30S}")
    @SchedulerLock(name = "StrategyBotForwardTestAlerting.refresh", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refreshAlertStateScheduled() {
        refreshAlertState();
    }

    public StrategyBotForwardTestSchedulerSnapshot refreshAlertState() {
        StrategyBotForwardTestSchedulerSnapshot snapshot = observabilityService.snapshot();
        AlertState previous = alertState.get();
        AlertState desired = evaluateState(snapshot);
        updateGauges(snapshot, desired);

        if (previous != desired) {
            transitionAlertState(previous, desired, snapshot);
        }

        return snapshot;
    }

    public StrategyBotForwardTestStatusSnapshot statusSnapshot() {
        StrategyBotForwardTestSchedulerSnapshot snapshot = refreshAlertState();
        return new StrategyBotForwardTestStatusSnapshot(
                snapshot.startedAt(),
                snapshot.checkedAt(),
                snapshot.refreshIntervalSeconds(),
                properties.normalizedStaleThreshold().toSeconds(),
                alertState.get().name(),
                resolveLastTickAgeSeconds(snapshot),
                snapshot.scheduledTickCount(),
                snapshot.lastObservedRunningRunCount(),
                snapshot.refreshAttemptCount(),
                snapshot.refreshSuccessCount(),
                snapshot.refreshFailureCount(),
                snapshot.refreshSkipCount(),
                snapshot.lastTickAt(),
                snapshot.lastRefreshAt(),
                snapshot.lastSkipAt(),
                snapshot.lastRefreshedRunId(),
                snapshot.lastRefreshedRunStatus(),
                snapshot.lastSkippedRunId(),
                snapshot.lastSkipReason(),
                snapshot.lastError()
        );
    }

    private AlertState evaluateState(StrategyBotForwardTestSchedulerSnapshot snapshot) {
        Duration staleThreshold = properties.normalizedStaleThreshold();
        LocalDateTime checkedAt = snapshot.checkedAt();

        if (snapshot.lastTickAt() == null) {
            if (snapshot.startedAt() == null) {
                return AlertState.WARNING;
            }
            long startupAgeSeconds = Math.max(Duration.between(snapshot.startedAt(), checkedAt).toSeconds(), 0);
            return startupAgeSeconds > staleThreshold.toSeconds() ? AlertState.WARNING : AlertState.NONE;
        }

        long lastTickAgeSeconds = Math.max(Duration.between(snapshot.lastTickAt(), checkedAt).toSeconds(), 0);
        return lastTickAgeSeconds > staleThreshold.toSeconds() ? AlertState.WARNING : AlertState.NONE;
    }

    private void transitionAlertState(AlertState previous,
                                      AlertState desired,
                                      StrategyBotForwardTestSchedulerSnapshot snapshot) {
        alertState.set(desired);

        if (desired == AlertState.NONE) {
            log.info("Strategy bot forward-test scheduler recovered. previousState={} lastTickAt={} scheduledTickCount={}",
                    previous, snapshot.lastTickAt(), snapshot.scheduledTickCount());
            if (properties.isAlertOnRecovery()) {
                opsAlertPublisher.publish(
                        COMPONENT,
                        OpsAlertSeverity.WARNING,
                        RECOVERY_ALERT_KEY,
                        "Strategy bot forward-test scheduler recovered",
                        snapshotDetails(snapshot, previous.name())
                );
            }
            return;
        }

        log.warn("Strategy bot forward-test scheduler became stale. previousState={} lastTickAt={} scheduledTickCount={}",
                previous, snapshot.lastTickAt(), snapshot.scheduledTickCount());
        opsAlertPublisher.publish(
                COMPONENT,
                OpsAlertSeverity.WARNING,
                ALERT_KEY,
                "Strategy bot forward-test scheduler stale threshold breached",
                snapshotDetails(snapshot, previous.name())
        );
    }

    private void updateGauges(StrategyBotForwardTestSchedulerSnapshot snapshot, AlertState desired) {
        alertStateGauge.set(desired.gaugeValue());
        lastTickAgeSecondsGauge.set(resolveLastTickAgeSeconds(snapshot));
    }

    private double resolveLastTickAgeSeconds(StrategyBotForwardTestSchedulerSnapshot snapshot) {
        if (snapshot.lastTickAt() == null || snapshot.checkedAt() == null) {
            return 0.0;
        }
        return Math.max(Duration.between(snapshot.lastTickAt(), snapshot.checkedAt()).toSeconds(), 0);
    }

    private Map<String, Object> snapshotDetails(StrategyBotForwardTestSchedulerSnapshot snapshot, String previousState) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousState", previousState);
        details.put("startedAt", snapshot.startedAt());
        details.put("checkedAt", snapshot.checkedAt());
        details.put("staleThresholdSeconds", properties.normalizedStaleThreshold().toSeconds());
        details.put("lastTickAt", snapshot.lastTickAt());
        details.put("lastTickAgeSeconds", resolveLastTickAgeSeconds(snapshot));
        details.put("scheduledTickCount", snapshot.scheduledTickCount());
        details.put("lastObservedRunningRunCount", snapshot.lastObservedRunningRunCount());
        details.put("refreshAttemptCount", snapshot.refreshAttemptCount());
        details.put("refreshSuccessCount", snapshot.refreshSuccessCount());
        details.put("refreshFailureCount", snapshot.refreshFailureCount());
        details.put("refreshSkipCount", snapshot.refreshSkipCount());
        details.put("lastRefreshAt", snapshot.lastRefreshAt());
        details.put("lastRefreshedRunId", snapshot.lastRefreshedRunId() == null ? "" : snapshot.lastRefreshedRunId().toString());
        details.put("lastRefreshedRunStatus", snapshot.lastRefreshedRunStatus() == null ? "" : snapshot.lastRefreshedRunStatus());
        details.put("lastSkippedRunId", snapshot.lastSkippedRunId() == null ? "" : snapshot.lastSkippedRunId().toString());
        details.put("lastSkipReason", snapshot.lastSkipReason() == null ? "" : snapshot.lastSkipReason());
        details.put("lastError", snapshot.lastError() == null ? "" : snapshot.lastError());
        return details;
    }

    private enum AlertState {
        NONE(0),
        WARNING(1);

        private final int gaugeValue;

        AlertState(int gaugeValue) {
            this.gaugeValue = gaugeValue;
        }

        int gaugeValue() {
            return gaugeValue;
        }
    }
}
