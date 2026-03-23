package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "idempotency")
@ConditionalOnProperty(name = "app.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyEndpoint {

    private final IdempotencyObservabilityService service;
    private final ObjectProvider<IdempotencyAlertingService> alertingServiceProvider;

    public IdempotencyEndpoint(
            IdempotencyObservabilityService service,
            ObjectProvider<IdempotencyAlertingService> alertingServiceProvider) {
        this.service = service;
        this.alertingServiceProvider = alertingServiceProvider;
    }

    @ReadOperation
    public Map<String, Object> idempotencyStatus() {
        try {
            IdempotencySnapshot snapshot = service.refreshSnapshot();
            refreshAlertState(snapshot);
            return toMap(snapshot);
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", true);
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return payload;
        }
    }

    @WriteOperation
    public Map<String, Object> cleanupExpired() {
        try {
            IdempotencySnapshot snapshot = service.cleanupExpiredNow();
            refreshAlertState(snapshot);
            return toMap(snapshot);
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("enabled", true);
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return payload;
        }
    }

    private Map<String, Object> toMap(IdempotencySnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", snapshot.checkedAt());
        payload.put("enabled", snapshot.enabled());
        payload.put("ttlSeconds", snapshot.ttlSeconds());
        payload.put("cleanupIntervalSeconds", snapshot.cleanupIntervalSeconds());
        payload.put("totalRecords", snapshot.totalRecords());
        payload.put("inProgressRecords", snapshot.inProgressRecords());
        payload.put("completedRecords", snapshot.completedRecords());
        payload.put("expiredRecords", snapshot.expiredRecords());
        payload.put("oldestExpiredAgeSeconds", snapshot.oldestExpiredAgeSeconds());
        payload.put("claimedCount", snapshot.claimedCount());
        payload.put("replayCount", snapshot.replayCount());
        payload.put("conflictCount", snapshot.conflictCount());
        payload.put("inProgressConflictCount", snapshot.inProgressConflictCount());
        payload.put("completedResponseCount", snapshot.completedResponseCount());
        payload.put("releasedCount", snapshot.releasedCount());
        payload.put("skippedLargeResponseCount", snapshot.skippedLargeResponseCount());
        payload.put("lastClaimedAt", snapshot.lastClaimedAt());
        payload.put("lastReplayAt", snapshot.lastReplayAt());
        payload.put("lastConflictAt", snapshot.lastConflictAt());
        payload.put("lastInProgressConflictAt", snapshot.lastInProgressConflictAt());
        payload.put("lastCompletedResponseAt", snapshot.lastCompletedResponseAt());
        payload.put("lastReleasedAt", snapshot.lastReleasedAt());
        payload.put("lastSkippedLargeResponseAt", snapshot.lastSkippedLargeResponseAt());
        payload.put("alertState", currentAlertState());
        payload.put("lastCleanupAt", snapshot.lastCleanupAt());
        payload.put("lastCleanupDeletedCount", snapshot.lastCleanupDeletedCount());
        payload.put("error", snapshot.error());
        return payload;
    }

    private void refreshAlertState(IdempotencySnapshot snapshot) {
        IdempotencyAlertingService alertingService = alertingServiceProvider.getIfAvailable();
        if (alertingService != null) {
            alertingService.refreshAlertState(snapshot);
        }
    }

    private String currentAlertState() {
        IdempotencyAlertingService alertingService = alertingServiceProvider.getIfAvailable();
        return alertingService == null ? "NONE" : alertingService.currentAlertState();
    }
}
