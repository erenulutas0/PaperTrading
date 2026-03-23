package com.finance.core.observability;

import com.finance.core.config.IdempotencyProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyAlertingService {

    private static final String COMPONENT = "idempotency-cleanup";
    private static final String ALERT_KEY = "cleanup-health-degraded";
    private static final String RECOVERY_ALERT_KEY = "cleanup-health-recovered";

    private final IdempotencyObservabilityService observabilityService;
    private final IdempotencyProperties properties;
    private final MeterRegistry meterRegistry;
    private final OpsAlertPublisher opsAlertPublisher;

    private final AtomicInteger alertStateGauge = new AtomicInteger(0);
    private final AtomicReference<Double> oldestExpiredAgeSecondsGauge = new AtomicReference<>(0.0);
    private final AtomicReference<AlertState> alertState = new AtomicReference<>(AlertState.NONE);

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge("app.idempotency.alert.state", alertStateGauge);
        meterRegistry.gauge("app.idempotency.oldest_expired_age.seconds", oldestExpiredAgeSecondsGauge, AtomicReference::get);
    }

    @Scheduled(fixedDelayString = "${app.idempotency.observability-refresh-interval:PT30S}")
    @SchedulerLock(name = "IdempotencyAlertingService.refresh", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refreshAlertStateScheduled() {
        refreshAlertState();
    }

    public IdempotencySnapshot refreshAlertState() {
        return refreshAlertState(observabilityService.refreshSnapshot());
    }

    IdempotencySnapshot refreshAlertState(IdempotencySnapshot snapshot) {
        AlertState previous = alertState.get();
        AlertState desired = evaluateState(snapshot);
        updateGauges(snapshot, desired);

        if (previous != desired) {
            transitionAlertState(previous, desired, snapshot);
        }

        return snapshot;
    }

    public String currentAlertState() {
        return alertState.get().name();
    }

    private AlertState evaluateState(IdempotencySnapshot snapshot) {
        if (snapshot.error() != null && !snapshot.error().isBlank()) {
            return AlertState.WARNING;
        }
        if (isCleanupLagging(snapshot)) {
            return AlertState.WARNING;
        }
        return AlertState.NONE;
    }

    private boolean isCleanupLagging(IdempotencySnapshot snapshot) {
        long cleanupIntervalSeconds = Math.max(snapshot.cleanupIntervalSeconds(), 1);
        return snapshot.expiredRecords() > 0
                && snapshot.oldestExpiredAgeSeconds() != null
                && snapshot.oldestExpiredAgeSeconds() > cleanupIntervalSeconds;
    }

    private void transitionAlertState(AlertState previous,
                                      AlertState desired,
                                      IdempotencySnapshot snapshot) {
        alertState.set(desired);

        if (desired == AlertState.NONE) {
            log.info("Idempotency cleanup observability recovered. previousState={} expiredRecords={} oldestExpiredAgeSeconds={}",
                    previous, snapshot.expiredRecords(), snapshot.oldestExpiredAgeSeconds());
            if (properties.isAlertOnRecovery()) {
                opsAlertPublisher.publish(
                        COMPONENT,
                        OpsAlertSeverity.WARNING,
                        RECOVERY_ALERT_KEY,
                        "Idempotency cleanup observability recovered",
                        snapshotDetails(snapshot, previous.name(), "healthy")
                );
            }
            return;
        }

        log.warn("Idempotency cleanup observability degraded. previousState={} expiredRecords={} oldestExpiredAgeSeconds={} error={}",
                previous, snapshot.expiredRecords(), snapshot.oldestExpiredAgeSeconds(), snapshot.error());
        opsAlertPublisher.publish(
                COMPONENT,
                OpsAlertSeverity.WARNING,
                ALERT_KEY,
                "Idempotency cleanup observability degraded",
                snapshotDetails(snapshot, previous.name(), snapshot.error() != null && !snapshot.error().isBlank()
                        ? "snapshot-unavailable"
                        : "cleanup-lagging")
        );
    }

    private void updateGauges(IdempotencySnapshot snapshot, AlertState desired) {
        alertStateGauge.set(desired.gaugeValue());
        oldestExpiredAgeSecondsGauge.set(snapshot.oldestExpiredAgeSeconds() == null ? 0.0 : snapshot.oldestExpiredAgeSeconds());
    }

    private Map<String, Object> snapshotDetails(IdempotencySnapshot snapshot, String previousState, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previousState", previousState);
        details.put("reason", reason);
        details.put("checkedAt", snapshot.checkedAt());
        details.put("cleanupIntervalSeconds", snapshot.cleanupIntervalSeconds());
        details.put("totalRecords", snapshot.totalRecords());
        details.put("inProgressRecords", snapshot.inProgressRecords());
        details.put("completedRecords", snapshot.completedRecords());
        details.put("expiredRecords", snapshot.expiredRecords());
        details.put("oldestExpiredAgeSeconds", snapshot.oldestExpiredAgeSeconds() == null ? 0.0 : snapshot.oldestExpiredAgeSeconds());
        details.put("claimedCount", snapshot.claimedCount());
        details.put("replayCount", snapshot.replayCount());
        details.put("conflictCount", snapshot.conflictCount());
        details.put("inProgressConflictCount", snapshot.inProgressConflictCount());
        details.put("completedResponseCount", snapshot.completedResponseCount());
        details.put("releasedCount", snapshot.releasedCount());
        details.put("skippedLargeResponseCount", snapshot.skippedLargeResponseCount());
        details.put("lastCleanupAt", snapshot.lastCleanupAt());
        details.put("lastCleanupDeletedCount", snapshot.lastCleanupDeletedCount());
        details.put("error", snapshot.error() == null ? "" : snapshot.error());
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
