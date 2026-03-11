package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Endpoint(id = "idempotency")
@ConditionalOnProperty(name = "app.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyEndpoint {

    private final IdempotencyObservabilityService service;

    public IdempotencyEndpoint(IdempotencyObservabilityService service) {
        this.service = service;
    }

    @ReadOperation
    public Map<String, Object> idempotencyStatus() {
        try {
            return toMap(service.refreshSnapshot());
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
        payload.put("lastCleanupAt", snapshot.lastCleanupAt());
        payload.put("lastCleanupDeletedCount", snapshot.lastCleanupDeletedCount());
        payload.put("error", snapshot.error());
        return payload;
    }
}
