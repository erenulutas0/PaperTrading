package com.finance.core.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditLogEntry;
import com.finance.core.repository.AuditLogRepository;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "auditlog")
public class AuditLogEndpoint {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogEndpoint(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @ReadOperation
    public Map<String, Object> auditLog(Integer limit, String requestId) {
        int safeLimit = normalizeLimit(limit);
        try {
            List<AuditLogEntry> entries = (requestId != null && !requestId.isBlank())
                    ? auditLogRepository.findByRequestIdOrderByCreatedAtDesc(requestId.trim()).stream().limit(safeLimit).toList()
                    : auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).getContent();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkedAt", LocalDateTime.now());
            payload.put("limit", safeLimit);
            payload.put("requestId", requestId);
            payload.put("count", entries.size());
            payload.put("entries", entries.stream().map(this::toPayload).toList());
            return payload;
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkedAt", LocalDateTime.now());
            payload.put("limit", safeLimit);
            payload.put("requestId", requestId);
            payload.put("count", 0);
            payload.put("entries", List.of());
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return payload;
        }
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private Map<String, Object> toPayload(AuditLogEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entry.getId());
        payload.put("actorId", entry.getActorId());
        payload.put("actionType", entry.getActionType());
        payload.put("resourceType", entry.getResourceType());
        payload.put("resourceId", entry.getResourceId());
        payload.put("requestId", entry.getRequestId());
        payload.put("ipAddress", entry.getIpAddress());
        payload.put("userAgent", entry.getUserAgent());
        payload.put("requestMethod", entry.getRequestMethod());
        payload.put("requestPath", entry.getRequestPath());
        payload.put("details", parseDetails(entry.getDetails()));
        payload.put("createdAt", entry.getCreatedAt());
        return payload;
    }

    private Object parseDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(details, Object.class);
        } catch (Exception ignored) {
            return details;
        }
    }
}
