package com.finance.core.controller;

import com.finance.core.service.AuditLogInspectionService;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops/auditlog")
@RequiredArgsConstructor
public class AuditOpsController {

    private final AuditLogInspectionService inspectionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> auditLog(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String requestId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(inspectionService.snapshot(limit, requestId));
        } catch (Throwable ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("requestId", request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE));
            payload.put("error", ex.getMessage());
            payload.put("fatal", true);
            return ResponseEntity.internalServerError().body(payload);
        }
    }
}
