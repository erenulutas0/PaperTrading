package com.finance.core.observability;

import com.finance.core.service.AuditLogInspectionService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "auditlog")
public class AuditLogEndpoint {

    private final AuditLogInspectionService inspectionService;

    public AuditLogEndpoint(AuditLogInspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @ReadOperation
    public Map<String, Object> auditLog() {
        try {
            return inspectionService.snapshot(null, null, null, null, null, null, null, null);
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("checkedAt", LocalDateTime.now());
            payload.put("limit", null);
            payload.put("page", null);
            payload.put("days", null);
            payload.put("requestId", null);
            payload.put("requestPath", null);
            payload.put("actorId", null);
            payload.put("actionType", null);
            payload.put("resourceType", null);
            payload.put("count", 0);
            payload.put("entries", List.of());
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return payload;
        }
    }
}
