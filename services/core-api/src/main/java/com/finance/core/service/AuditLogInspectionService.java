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

    public Map<String, Object> snapshot(Integer limit, Integer page, Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        int safeLimit = normalizeLimit(limit);
        int safePage = normalizePage(page);
        Integer safeDays = normalizeDays(days);
        long totalCount = countEntries(safeDays, requestId, actorId, actionType, resourceType);
        List<Map<String, Object>> entries = fetchEntries(safeLimit, safePage, safeDays, requestId, actorId, actionType, resourceType);
        Map<String, Object> facets = buildFacetSummary(safeDays, requestId, actorId, actionType, resourceType);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", LocalDateTime.now());
        payload.put("limit", safeLimit);
        payload.put("page", safePage);
        payload.put("days", safeDays);
        payload.put("requestId", requestId);
        payload.put("actorId", actorId);
        payload.put("actionType", actionType);
        payload.put("resourceType", resourceType);
        payload.put("count", entries.size());
        payload.put("totalCount", totalCount);
        payload.put("hasMore", ((long) (safePage + 1) * safeLimit) < totalCount);
        payload.put("facets", facets);
        payload.put("entries", entries);
        return payload;
    }

    public String exportCsv(Integer limit, Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        int safeLimit = normalizeLimit(limit);
        Integer safeDays = normalizeDays(days);
        List<Map<String, Object>> entries = fetchEntries(safeLimit, 0, safeDays, requestId, actorId, actionType, resourceType);
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

    private List<Map<String, Object>> fetchEntries(int limit, int page, Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        String baseSql = """
                select id, actor_id, action_type, resource_type, resource_id, request_id,
                       ip_address, user_agent, request_method, request_path, details, created_at
                from audit_logs
                """;
        QueryParts queryParts = buildWhereClause(days, requestId, actorId, actionType, resourceType);
        List<Object> params = new ArrayList<>(queryParts.params());

        StringBuilder sql = new StringBuilder(baseSql);
        if (!queryParts.clauses().isEmpty()) {
            sql.append(" where ").append(String.join(" and ", queryParts.clauses()));
        }
        sql.append(" order by created_at desc limit ? offset ?");
        params.add(limit);
        params.add(page * limit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> toPayload(rs),
                params.toArray());
    }

    private long countEntries(Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        StringBuilder sql = new StringBuilder("select count(*) from audit_logs");
        QueryParts queryParts = buildWhereClause(days, requestId, actorId, actionType, resourceType);
        if (!queryParts.clauses().isEmpty()) {
            sql.append(" where ").append(String.join(" and ", queryParts.clauses()));
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, queryParts.params().toArray());
        return count == null ? 0L : count;
    }

    private Map<String, Object> buildFacetSummary(Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("actions", fetchFacetCounts("action_type", days, requestId, actorId, actionType, resourceType, false));
        facets.put("resources", fetchFacetCounts("resource_type", days, requestId, actorId, actionType, resourceType, false));
        facets.put("actors", fetchFacetCounts("actor_id", days, requestId, actorId, actionType, resourceType, true));
        return facets;
    }

    private List<Map<String, Object>> fetchFacetCounts(
            String column,
            Integer days,
            String requestId,
            UUID actorId,
            AuditActionType actionType,
            AuditResourceType resourceType,
            boolean excludeNulls) {
        StringBuilder sql = new StringBuilder("select ")
                .append(column)
                .append(" as value, count(*) as count from audit_logs");
        QueryParts queryParts = buildWhereClause(days, requestId, actorId, actionType, resourceType);
        List<String> clauses = new ArrayList<>(queryParts.clauses());
        List<Object> params = new ArrayList<>(queryParts.params());
        if (excludeNulls) {
            clauses.add(column + " is not null");
        }
        if (!clauses.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", clauses));
        }
        sql.append(" group by ").append(column).append(" order by count(*) desc, ").append(column).append(" asc limit 6");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("value", rs.getString("value"));
            item.put("count", rs.getLong("count"));
            return item;
        }, params.toArray());
    }

    private QueryParts buildWhereClause(Integer days, String requestId, UUID actorId, AuditActionType actionType, AuditResourceType resourceType) {
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

        return new QueryParts(clauses, params);
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

    private int normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        return Math.max(0, page);
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

    private record QueryParts(List<String> clauses, List<Object> params) {
    }
}
