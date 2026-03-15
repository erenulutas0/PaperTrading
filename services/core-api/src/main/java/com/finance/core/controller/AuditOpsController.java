package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.service.AuditLogInspectionService;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops/auditlog")
@RequiredArgsConstructor
public class AuditOpsController {

    private final AuditLogInspectionService inspectionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> auditLog(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditResourceType resourceType,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(inspectionService.snapshot(limit, page, days, requestId, requestPath, actorId, actionType, resourceType));
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE));
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return ResponseEntity.internalServerError().body(payload);
        }
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportAuditLog(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditResourceType resourceType) {
        String csv = inspectionService.exportCsv(limit, days, requestId, requestPath, actorId, actionType, resourceType);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("audit-log-export.csv")
                        .build()
                        .toString())
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportAuditLogJson(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditResourceType resourceType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("audit-log-view.json")
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(inspectionService.exportJson(limit, page, days, requestId, requestPath, actorId, actionType, resourceType));
    }
}
