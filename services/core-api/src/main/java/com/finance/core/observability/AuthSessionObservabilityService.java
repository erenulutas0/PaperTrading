package com.finance.core.observability;

import com.finance.core.config.AuthObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.auth.observability.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionObservabilityService {

    private static final String COMPONENT = "auth-refresh-churn";
    private static final String ALERT_KEY = "refresh-churn-breach";
    private static final String RECOVERY_ALERT_KEY = "refresh-churn-recovered";

    private final MeterRegistry meterRegistry;
    private final AuthObservabilityProperties properties;
    private final OpsAlertPublisher opsAlertPublisher;

    private final AtomicReference<AuthSessionChurnSnapshot> latestSnapshot =
            new AtomicReference<>(AuthSessionChurnSnapshot.empty());
    private final AtomicLong refreshSuccessGauge = new AtomicLong(0);
    private final AtomicLong invalidRefreshGauge = new AtomicLong(0);
    private final AtomicReference<Double> invalidRatioGauge = new AtomicReference<>(0.0);
    private final AtomicInteger alertStateGauge = new AtomicInteger(0);
    private final AtomicReference<AlertState> alertState = new AtomicReference<>(AlertState.NONE);

    private final Object eventLock = new Object();
    private final Deque<Instant> refreshSuccessEvents = new ArrayDeque<>();
    private final Deque<Instant> invalidRefreshEvents = new ArrayDeque<>();

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge("app.auth.refresh.window.success", refreshSuccessGauge);
        meterRegistry.gauge("app.auth.refresh.window.invalid", invalidRefreshGauge);
        meterRegistry.gauge("app.auth.refresh.window.invalid.ratio", invalidRatioGauge, AtomicReference::get);
        meterRegistry.gauge("app.auth.refresh.alert.state", alertStateGauge);
    }

    public void recordRefreshSuccess() {
        appendEvent(refreshSuccessEvents);
    }

    public void recordInvalidRefreshAttempt() {
        appendEvent(invalidRefreshEvents);
    }

    @Scheduled(fixedDelayString = "${app.auth.observability.refresh-interval:PT30S}")
    public void refreshSnapshotScheduled() {
        refreshSnapshot();
    }

    public AuthSessionChurnSnapshot getLatestSnapshot() {
        AuthSessionChurnSnapshot snapshot = latestSnapshot.get();
        if (snapshot.checkedAt() == null) {
            return refreshSnapshot();
        }

        LocalDateTime nextRefreshAt = snapshot.checkedAt().plus(properties.normalizedRefreshInterval());
        if (LocalDateTime.now().isAfter(nextRefreshAt)) {
            return refreshSnapshot();
        }

        return snapshot;
    }

    public AuthSessionChurnSnapshot refreshSnapshot() {
        try {
            AuthSessionChurnSnapshot snapshot = collectSnapshot();
            AlertState previous = alertState.get();
            AlertState desired = evaluateAlertState(snapshot);
            transitionAlertStateIfNeeded(previous, desired, snapshot);

            AuthSessionChurnSnapshot updatedSnapshot = snapshot.withAlertState(desired.name());
            latestSnapshot.set(updatedSnapshot);
            updateGauges(updatedSnapshot);
            return updatedSnapshot;
        } catch (Exception ex) {
            log.error("Failed to collect auth session churn snapshot", ex);
            AuthSessionChurnSnapshot errorSnapshot = new AuthSessionChurnSnapshot(
                    LocalDateTime.now(),
                    properties.normalizedChurnWindow().toSeconds(),
                    properties.normalizedMinSamples(),
                    properties.normalizedWarningRefreshCount(),
                    properties.normalizedCriticalRefreshCount(),
                    properties.normalizedWarningInvalidCount(),
                    properties.normalizedCriticalInvalidCount(),
                    properties.normalizedWarningInvalidRatio(),
                    properties.normalizedCriticalInvalidRatio(),
                    0,
                    0,
                    0,
                    0.0,
                    false,
                    false,
                    alertState.get().name(),
                    ex.getMessage()
            );
            latestSnapshot.set(errorSnapshot);
            updateGauges(errorSnapshot);
            return errorSnapshot;
        }
    }

    private AuthSessionChurnSnapshot collectSnapshot() {
        Instant now = Instant.now();
        Duration window = properties.normalizedChurnWindow();
        Instant cutoff = now.minus(window);
        long successCount;
        long invalidCount;

        synchronized (eventLock) {
            pruneBefore(refreshSuccessEvents, cutoff);
            pruneBefore(invalidRefreshEvents, cutoff);
            successCount = refreshSuccessEvents.size();
            invalidCount = invalidRefreshEvents.size();
        }

        long totalAttempts = successCount + invalidCount;
        double invalidRatio = totalAttempts <= 0 ? 0.0 : ((double) invalidCount) / totalAttempts;
        long minSamples = properties.normalizedMinSamples();
        long warningRefresh = properties.normalizedWarningRefreshCount();
        long criticalRefresh = properties.normalizedCriticalRefreshCount();
        long warningInvalid = properties.normalizedWarningInvalidCount();
        long criticalInvalid = properties.normalizedCriticalInvalidCount();
        double warningInvalidRatio = properties.normalizedWarningInvalidRatio();
        double criticalInvalidRatio = properties.normalizedCriticalInvalidRatio();

        boolean enoughSamples = totalAttempts >= minSamples;
        boolean warningBreach = enoughSamples
                && (successCount >= warningRefresh
                || invalidCount >= warningInvalid
                || invalidRatio >= warningInvalidRatio);
        boolean criticalBreach = enoughSamples
                && (successCount >= criticalRefresh
                || invalidCount >= criticalInvalid
                || invalidRatio >= criticalInvalidRatio);

        return new AuthSessionChurnSnapshot(
                LocalDateTime.now(),
                window.toSeconds(),
                minSamples,
                warningRefresh,
                criticalRefresh,
                warningInvalid,
                criticalInvalid,
                warningInvalidRatio,
                criticalInvalidRatio,
                successCount,
                invalidCount,
                totalAttempts,
                invalidRatio,
                warningBreach,
                criticalBreach,
                alertState.get().name(),
                null
        );
    }

    private void appendEvent(Deque<Instant> events) {
        synchronized (eventLock) {
            events.addLast(Instant.now());
        }
    }

    private void pruneBefore(Deque<Instant> events, Instant cutoff) {
        while (!events.isEmpty() && events.peekFirst().isBefore(cutoff)) {
            events.removeFirst();
        }
    }

    private AlertState evaluateAlertState(AuthSessionChurnSnapshot snapshot) {
        if (snapshot.criticalBreach()) {
            return AlertState.CRITICAL;
        }
        if (snapshot.warningBreach()) {
            return AlertState.WARNING;
        }
        return AlertState.NONE;
    }

    private void transitionAlertStateIfNeeded(AlertState previous,
                                              AlertState desired,
                                              AuthSessionChurnSnapshot snapshot) {
        if (previous == desired) {
            return;
        }

        meterRegistry.counter(
                "app.auth.refresh.state.transitions",
                "from", previous.name().toLowerCase(),
                "to", desired.name().toLowerCase()
        ).increment();

        alertState.set(desired);

        if (desired == AlertState.NONE) {
            log.info("Auth refresh churn recovered. previousState={} totalAttempts={} invalidRatio={}",
                    previous, snapshot.totalRefreshAttempts(), snapshot.invalidRefreshRatio());
            if (properties.isAlertOnRecovery()) {
                opsAlertPublisher.publish(
                        COMPONENT,
                        OpsAlertSeverity.WARNING,
                        RECOVERY_ALERT_KEY,
                        "Auth refresh churn recovered",
                        snapshotDetails(snapshot, previous.name())
                );
            }
            return;
        }

        log.warn("Auth refresh churn state transition: {} -> {} (attempts={} invalidRatio={})",
                previous, desired, snapshot.totalRefreshAttempts(), snapshot.invalidRefreshRatio());

        opsAlertPublisher.publish(
                COMPONENT,
                desired.toSeverity(),
                ALERT_KEY,
                "Auth refresh churn threshold breached",
                snapshotDetails(snapshot, previous.name())
        );
    }

    private Map<String, Object> snapshotDetails(AuthSessionChurnSnapshot snapshot, String previousState) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousState", previousState);
        details.put("windowSeconds", snapshot.windowSeconds());
        details.put("minSamples", snapshot.minSamples());
        details.put("refreshSuccessCount", snapshot.refreshSuccessCount());
        details.put("invalidRefreshCount", snapshot.invalidRefreshCount());
        details.put("totalRefreshAttempts", snapshot.totalRefreshAttempts());
        details.put("invalidRefreshRatio", snapshot.invalidRefreshRatio());
        details.put("warningRefreshCount", snapshot.warningRefreshCount());
        details.put("criticalRefreshCount", snapshot.criticalRefreshCount());
        details.put("warningInvalidCount", snapshot.warningInvalidCount());
        details.put("criticalInvalidCount", snapshot.criticalInvalidCount());
        details.put("warningInvalidRatio", snapshot.warningInvalidRatio());
        details.put("criticalInvalidRatio", snapshot.criticalInvalidRatio());
        return details;
    }

    private void updateGauges(AuthSessionChurnSnapshot snapshot) {
        refreshSuccessGauge.set(snapshot.refreshSuccessCount());
        invalidRefreshGauge.set(snapshot.invalidRefreshCount());
        invalidRatioGauge.set(snapshot.invalidRefreshRatio());
        alertStateGauge.set(AlertState.from(snapshot.alertState()).gaugeValue());
    }

    private enum AlertState {
        NONE(0),
        WARNING(1),
        CRITICAL(2);

        private final int gaugeValue;

        AlertState(int gaugeValue) {
            this.gaugeValue = gaugeValue;
        }

        int gaugeValue() {
            return gaugeValue;
        }

        OpsAlertSeverity toSeverity() {
            return this == CRITICAL ? OpsAlertSeverity.CRITICAL : OpsAlertSeverity.WARNING;
        }

        static AlertState from(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            try {
                return AlertState.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return NONE;
            }
        }
    }
}
