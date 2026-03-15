package com.finance.core.observability;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.service.AuditLogInspectionService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Endpoint(id = "auditlog")
public class AuditLogEndpoint {

    private final AuditLogInspectionService inspectionService;

    public AuditLogEndpoint(AuditLogInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @ReadOperation
    public Map<String, Object> auditLog(
            @Nullable Integer limit,
            @Nullable Integer page,
            @Nullable Integer days,
            @Nullable String requestId,
            @Nullable String requestPath,
            @Nullable UUID actorId,
            @Nullable AuditActionType actionType,
            @Nullable AuditResourceType resourceType) {
        try {
            return inspectionService.snapshot(limit, page, days, requestId, requestPath, actorId, actionType, resourceType);
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkedAt", LocalDateTime.now());
            payload.put("limit", limit);
            payload.put("page", page);
            payload.put("days", days);
            payload.put("requestId", requestId);
            payload.put("requestPath", requestPath);
            payload.put("actorId", actorId);
            payload.put("actionType", actionType);
            payload.put("resourceType", resourceType);
            payload.put("count", 0);
            payload.put("entries", List.of());
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return payload;
        }
    }
}
