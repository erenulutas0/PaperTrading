package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public Map<String, Object> snapshot(Integer limit, String requestId) {
        int safeLimit = normalizeLimit(limit);
        List<Map<String, Object>> entries = fetchEntries(safeLimit, requestId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", LocalDateTime.now());
        payload.put("limit", safeLimit);
        payload.put("requestId", requestId);
        payload.put("count", entries.size());
        payload.put("entries", entries);
        return payload;
    }

    private List<Map<String, Object>> fetchEntries(int limit, String requestId) {
        String baseSql = """
                select id, actor_id, action_type, resource_type, resource_id, request_id,
                       ip_address, user_agent, request_method, request_path, details, created_at
                from audit_logs
                """;

        if (requestId != null && !requestId.isBlank()) {
            return jdbcTemplate.query(
                    baseSql + " where request_id = ? order by created_at desc limit ?",
                    (rs, rowNum) -> toPayload(rs),
                    requestId.trim(),
                    limit);
        }

        return jdbcTemplate.query(
                baseSql + " order by created_at desc limit ?",
                (rs, rowNum) -> toPayload(rs),
                limit);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
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
}
