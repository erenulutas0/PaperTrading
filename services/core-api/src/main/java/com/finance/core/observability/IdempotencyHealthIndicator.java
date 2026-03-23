package com.finance.core.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("idempotency")
@ConditionalOnProperty(name = "app.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyHealthIndicator implements HealthIndicator {

    private final IdempotencyObservabilityService service;

    public IdempotencyHealthIndicator(IdempotencyObservabilityService service) {
        this.service = service;
    }

    @Override
    public Health health() {
        IdempotencySnapshot snapshot = service.refreshSnapshot();
        if (snapshot.error() != null && !snapshot.error().isBlank()) {
            return Health.down()
                    .withDetail("status", "snapshot-unavailable")
                    .withDetail("error", snapshot.error())
                    .build();
        }

        long cleanupIntervalSeconds = Math.max(snapshot.cleanupIntervalSeconds(), 1);
        boolean staleExpiredBacklog = snapshot.expiredRecords() > 0
                && snapshot.oldestExpiredAgeSeconds() != null
                && snapshot.oldestExpiredAgeSeconds() > cleanupIntervalSeconds;

        Health.Builder builder = staleExpiredBacklog ? Health.down() : Health.up();
        return builder
                .withDetail("status", staleExpiredBacklog ? "cleanup-lagging" : "healthy")
                .withDetail("cleanupIntervalSeconds", cleanupIntervalSeconds)
                .withDetail("expiredBacklogThresholdSeconds", cleanupIntervalSeconds)
                .withDetail("totalRecords", snapshot.totalRecords())
                .withDetail("inProgressRecords", snapshot.inProgressRecords())
                .withDetail("completedRecords", snapshot.completedRecords())
                .withDetail("expiredRecords", snapshot.expiredRecords())
                .withDetail("oldestExpiredAgeSeconds", detailValue(snapshot.oldestExpiredAgeSeconds()))
                .withDetail("claimedCount", snapshot.claimedCount())
                .withDetail("replayCount", snapshot.replayCount())
                .withDetail("conflictCount", snapshot.conflictCount())
                .withDetail("inProgressConflictCount", snapshot.inProgressConflictCount())
                .withDetail("completedResponseCount", snapshot.completedResponseCount())
                .withDetail("releasedCount", snapshot.releasedCount())
                .withDetail("skippedLargeResponseCount", snapshot.skippedLargeResponseCount())
                .withDetail("lastCleanupAt", detailValue(snapshot.lastCleanupAt()))
                .withDetail("lastCleanupDeletedCount", snapshot.lastCleanupDeletedCount())
                .build();
    }

    private Object detailValue(Object value) {
        return value == null ? "" : value;
    }
}
