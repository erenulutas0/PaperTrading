package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditLogEntry;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.repository.AuditLogRepository;
import com.finance.core.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogEntry record(
            UUID actorId,
            AuditActionType actionType,
            AuditResourceType resourceType,
            UUID resourceId,
            Map<String, Object> details) {

        RequestSnapshot requestSnapshot = resolveRequestSnapshot();
        AuditLogEntry entry = AuditLogEntry.builder()
                .actorId(actorId)
                .actionType(actionType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .requestId(requestSnapshot.requestId())
                .ipAddress(requestSnapshot.ipAddress())
                .userAgent(requestSnapshot.userAgent())
                .requestMethod(requestSnapshot.requestMethod())
                .requestPath(requestSnapshot.requestPath())
                .details(serializeDetails(details))
                .build();

        AuditLogEntry saved = auditLogRepository.save(entry);
        log.debug("Audit logged action={} resourceType={} resourceId={} actorId={}",
                actionType, resourceType, resourceId, actorId);
        return saved;
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit details", e);
        }
    }

    private RequestSnapshot resolveRequestSnapshot() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return RequestSnapshot.empty();
        }

        HttpServletRequest request = servletAttributes.getRequest();
        Object requestId = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        return new RequestSnapshot(
                requestId instanceof String value ? value : null,
                resolveClientIp(request),
                trimToNull(request.getHeader("User-Agent")),
                trimToNull(request.getMethod()),
                trimToNull(request.getRequestURI()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return trimToNull(request.getRemoteAddr());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record RequestSnapshot(
            String requestId,
            String ipAddress,
            String userAgent,
            String requestMethod,
            String requestPath) {

        private static RequestSnapshot empty() {
            return new RequestSnapshot(null, null, null, null, null);
        }
    }
}
