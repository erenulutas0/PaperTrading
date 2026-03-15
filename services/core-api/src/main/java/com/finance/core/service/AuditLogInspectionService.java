package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AuditLogInspectionService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogInspectionService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> snapshot(Integer limit, Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        int safeLimit = normalizeLimit(limit);
        Integer safeDays = normalizeDays(days);
        List<Map<String, Object>> entries = fetchEntries(safeLimit, safeDays, requestId, actorId, actionType, resourceType);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", LocalDateTime.now());
        payload.put("limit", safeLimit);
        payload.put("days", safeDays);
        payload.put("requestId", requestId);
        payload.put("actorId", actorId);
        payload.put("actionType", actionType);
        payload.put("resourceType", resourceType);
        payload.put("count", entries.size());
        payload.put("entries", entries);
        return payload;
    }

    public String exportCsv(Integer limit, Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        int safeLimit = normalizeLimit(limit);
        Integer safeDays = normalizeDays(days);
        List<Map<String, Object>> entries = fetchEntries(safeLimit, safeDays, requestId, actorId, actionType, resourceType);
        List<String> rows = new ArrayList<>();
        rows.add("id,actorId,actionType,resourceType,resourceId,requestId,ipAddress,requestMethod,requestPath,createdAt");
        entries.forEach(entry -> rows.add(String.join(",",
                csv(entry.get("id")),
                csv(entry.get("actorId")),
                csv(entry.get("actionType")),
                csv(entry.get("resourceType")),
                csv(entry.get("resourceId")),
                csv(entry.get("requestId")),
                csv(entry.get("ipAddress")),
                csv(entry.get("requestMethod")),
                csv(entry.get("requestPath")),
                csv(entry.get("createdAt"))
        )));
        return rows.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    private List<Map<String, Object>> fetchEntries(int limit, Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        String baseSql = """
                select id, actor_id, action_type, resource_type, resource_id, request_id,
                       ip_address, user_agent, request_method, request_path, details, created_at
                from audit_logs
                """;
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (requestId != null && !requestId.isBlank()) {
            clauses.add("request_id = ?");
            params.add(requestId.trim());
        }
        if (actorId != null) {
            clauses.add("actor_id = ?");
            params.add(actorId);
        }
        if (actionType != null) {
            clauses.add("action_type = ?");
            params.add(actionType.name());
        }
        if (resourceType != null) {
            clauses.add("resource_type = ?");
            params.add(resourceType.name());
        }
        if (days != null) {
            clauses.add("created_at >= ?");
            params.add(Timestamp.valueOf(LocalDateTime.now().minusDays(days)));
        }

        StringBuilder sql = new StringBuilder(baseSql);
        if (!clauses.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", clauses));
        }
        sql.append(" order by created_at desc limit ?");
        params.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> toPayload(rs),
                params.toArray());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private Integer normalizeDays(Integer days) {
        if (days == null) {
            return null;
        }
        return Math.max(1, Math.min(365, days));
    }

    private Map<String, Object> toPayload(ResultSet rs) throws SQLException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", readUuid(rs, "id"));
        payload.put("actorId", readUuid(rs, "actor_id"));
        payload.put("actionType", rs.getString("action_type"));
        payload.put("resourceType", rs.getString("resource_type"));
        payload.put("resourceId", readUuid(rs, "resource_id"));
        payload.put("requestId", rs.getString("request_id"));
        payload.put("ipAddress", rs.getString("ip_address"));
        payload.put("userAgent", rs.getString("user_agent"));
        payload.put("requestMethod", rs.getString("request_method"));
        payload.put("requestPath", rs.getString("request_path"));
        payload.put("details", parseDetails(rs.getString("details")));
        payload.put("createdAt", readLocalDateTime(rs, "created_at"));
        return payload;
    }

    private UUID readUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private LocalDateTime readLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Object parseDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(details, Object.class);
        } catch (Exception ex) {
            log.warn("Failed to parse audit details payload, returning raw text", ex);
            return details;
        }
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String raw = value.toString().replace("\"", "\"\"");
        return "\"" + raw + "\"";
    }
}
