package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.service.AuditLogInspectionService;
import com.finance.core.web.ApiErrorResponses;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops/auditlog")
@RequiredArgsConstructor
public class AuditOpsController {

    private final AuditLogInspectionService inspectionService;

    @GetMapping
    public ResponseEntity<?> auditLog(
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
        } catch (Exception ex) {
            return ApiErrorResponses.build(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "audit_snapshot_failed",
                    "Failed to inspect audit log",
                    null,
                    request);
        }
    }

    @GetMapping(value = "/export", produces = {"text/csv", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> exportAuditLog(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String requestPath,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) AuditResourceType resourceType,
            HttpServletRequest request) {
        try {
            String csv = inspectionService.exportCsv(limit, days, requestId, requestPath, actorId, actionType, resourceType);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("audit-log-export.csv")
                            .build()
                            .toString())
                    .contentType(new MediaType("text", "csv"))
                    .body(csv);
        } catch (Exception ex) {
            return ApiErrorResponses.buildJson(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "audit_export_failed",
                    "Failed to export audit log",
                    null,
                    request);
        }
    }

    @GetMapping(value = "/export/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportAuditLogJson(
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
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("audit-log-view.json")
                            .build()
                            .toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(inspectionService.exportJson(limit, page, days, requestId, requestPath, actorId, actionType, resourceType));
        } catch (Exception ex) {
            return ApiErrorResponses.build(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "audit_export_json_failed",
                    "Failed to export audit log view",
                    null,
                    request);
        }
    }
}
