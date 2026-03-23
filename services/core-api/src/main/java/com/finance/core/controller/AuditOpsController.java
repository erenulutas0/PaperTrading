package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.service.AuditLogInspectionService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops/auditlog")
@RequiredArgsConstructor
public class AuditOpsController {

    private static final int MAX_LIMIT = 100;
    private static final int MAX_DAYS = 365;

    private final AuditLogInspectionService inspectionService;

    @GetMapping
    public ResponseEntity<?> auditLog(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            HttpServletRequest request) {
        AuditFilters filters = parseFilters(limit, page, days, requestId, requestPath, actorId, actionType, resourceType, false);
        try {
            return ResponseEntity.ok(inspectionService.snapshot(
                    filters.limit(),
                    filters.page(),
                    filters.days(),
                    filters.requestId(),
                    filters.requestPath(),
                    filters.actorId(),
                    filters.actionType(),
                    filters.resourceType()));
        } catch (Exception ex) {
            return buildAuditError("audit_snapshot_failed", "Failed to inspect audit log", request, false);
        }
    }

    @GetMapping(value = "/export", produces = {"text/csv", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> exportAuditLog(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            HttpServletRequest request) {
        AuditFilters filters = parseFilters(limit, null, days, requestId, requestPath, actorId, actionType, resourceType, true);
        try {
            String csv = inspectionService.exportCsv(
                    filters.limit(),
                    filters.days(),
                    filters.requestId(),
                    filters.requestPath(),
                    filters.actorId(),
                    filters.actionType(),
                    filters.resourceType());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("audit-log-export.csv")
                            .build()
                            .toString())
                    .contentType(new MediaType("text", "csv"))
                    .body(csv);
        } catch (Exception ex) {
            return buildAuditError("audit_export_failed", "Failed to export audit log", request, true);
        }
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportAuditLogJson(
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String resourceType,
            HttpServletRequest request) {
        AuditFilters filters = parseFilters(limit, page, days, requestId, requestPath, actorId, actionType, resourceType, false);
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("audit-log-view.json")
                            .build()
                            .toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(inspectionService.exportJson(
                            filters.limit(),
                            filters.page(),
                            filters.days(),
                            filters.requestId(),
                            filters.requestPath(),
                            filters.actorId(),
                            filters.actionType(),
                            filters.resourceType()));
        } catch (Exception ex) {
            return buildAuditError("audit_export_json_failed", "Failed to export audit log view", request, false);
        }
    }

    private AuditFilters parseFilters(
            String limit,
            String page,
            String days,
            String requestId,
            String requestPath,
            String actorId,
            String actionType,
            String resourceType,
            boolean forceJson) {
        return new AuditFilters(
                parseLimit(limit, forceJson),
                parsePage(page, forceJson),
                parseDays(days, forceJson),
                normalizeText(requestId),
                normalizeText(requestPath),
                parseActorId(actorId, forceJson),
                parseActionType(actionType, forceJson),
                parseResourceType(resourceType, forceJson));
    }

    private Integer parseLimit(String raw, boolean forceJson) {
        Integer value = parseInteger(raw, "invalid_audit_limit", "Invalid audit limit", forceJson);
        if (value == null) {
            return null;
        }
        if (value < 1 || value > MAX_LIMIT) {
            throw invalidAuditRequest("invalid_audit_limit", "Invalid audit limit", forceJson);
        }
        return value;
    }

    private Integer parsePage(String raw, boolean forceJson) {
        Integer value = parseInteger(raw, "invalid_audit_page", "Invalid audit page", forceJson);
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw invalidAuditRequest("invalid_audit_page", "Invalid audit page", forceJson);
        }
        return value;
    }

    private Integer parseDays(String raw, boolean forceJson) {
        Integer value = parseInteger(raw, "invalid_audit_days", "Invalid audit days", forceJson);
        if (value == null) {
            return null;
        }
        if (value < 1 || value > MAX_DAYS) {
            throw invalidAuditRequest("invalid_audit_days", "Invalid audit days", forceJson);
        }
        return value;
    }

    private Integer parseInteger(String raw, String code, String message, boolean forceJson) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return Integer.valueOf(normalized);
        } catch (NumberFormatException exception) {
            throw invalidAuditRequest(code, message, forceJson);
        }
    }

    private UUID parseActorId(String raw, boolean forceJson) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException exception) {
            throw invalidAuditRequest("invalid_audit_actor_id", "Invalid audit actor id", forceJson);
        }
    }

    private AuditActionType parseActionType(String raw, boolean forceJson) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return AuditActionType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidAuditRequest("invalid_audit_action_type", "Invalid audit action type", forceJson);
        }
    }

    private AuditResourceType parseResourceType(String raw, boolean forceJson) {
        String normalized = normalizeText(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return AuditResourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidAuditRequest("invalid_audit_resource_type", "Invalid audit resource type", forceJson);
        }
    }

    private String normalizeText(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResponseEntity<?> buildAuditError(
            String fallbackCode,
            String fallbackMessage,
            HttpServletRequest request,
            boolean forceJson) {
        return buildAuditApiError(HttpStatus.INTERNAL_SERVER_ERROR, fallbackCode, fallbackMessage, request, forceJson);
    }

    private ResponseEntity<?> buildAuditApiError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            boolean forceJson) {
        if (forceJson) {
            return ApiErrorResponses.buildJson(status, code, message, null, request);
        }
        return ApiErrorResponses.build(status, code, message, null, request);
    }

    private ApiRequestException invalidAuditRequest(String code, String message, boolean forceJson) {
        return forceJson
                ? ApiRequestException.badRequestJson(code, message)
                : ApiRequestException.badRequest(code, message);
    }

    private record AuditFilters(
            Integer limit,
            Integer page,
            Integer days,
            String requestId,
            String requestPath,
            UUID actorId,
            AuditActionType actionType,
            AuditResourceType resourceType) {
    }
}
