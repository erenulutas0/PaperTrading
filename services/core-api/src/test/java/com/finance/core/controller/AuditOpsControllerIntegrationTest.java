package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditLogEntry;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.repository.AuditLogRepository;
import com.finance.core.service.AuditLogInspectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@AutoConfigureMockMvc
class AuditOpsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private AuditLogInspectionService inspectionService;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void auditOpsEndpoint_shouldExposeRecentAuditRows() throws Exception {
        UUID actorId = UUID.randomUUID();
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(actorId)
                .actionType(AuditActionType.PORTFOLIO_CREATED)
                .resourceType(AuditResourceType.PORTFOLIO)
                .resourceId(UUID.randomUUID())
                .requestId("req-ops-audit")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .details("{\"name\":\"Ops Smoke\"}")
                .createdAt(java.time.LocalDateTime.now())
                .build());
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.USER_FOLLOWED)
                .resourceType(AuditResourceType.USER)
                .resourceId(UUID.randomUUID())
                .requestId("req-other")
                .requestMethod("POST")
                .requestPath("/api/v1/users/follow")
                .details("{\"target\":\"user-1\"}")
                .createdAt(java.time.LocalDateTime.now().minusDays(10))
                .build());

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .param("limit", "5")
                        .param("requestId", "req-ops-audit")
                        .param("actorId", actorId.toString())
                        .param("actionType", "PORTFOLIO_CREATED")
                        .param("resourceType", "PORTFOLIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.actorId").value(actorId.toString()))
                .andExpect(jsonPath("$.actionType").value("PORTFOLIO_CREATED"))
                .andExpect(jsonPath("$.resourceType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.facets.actions[0].value").value("PORTFOLIO_CREATED"))
                .andExpect(jsonPath("$.facets.resources[0].value").value("PORTFOLIO"))
                .andExpect(jsonPath("$.facets.actors[0].value").value(actorId.toString()))
                .andExpect(jsonPath("$.entries[0].requestId").value("req-ops-audit"))
                .andExpect(jsonPath("$.entries[0].details.name").value("Ops Smoke"));
    }

    @Test
    void auditOpsEndpoint_shouldFilterByRequestPath() throws Exception {
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.TRADE_BUY_EXECUTED)
                .resourceType(AuditResourceType.TRADE)
                .resourceId(UUID.randomUUID())
                .requestId("req-path-1")
                .requestMethod("POST")
                .requestPath("/api/v1/trades/buy")
                .build());
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.TRADE_SELL_EXECUTED)
                .resourceType(AuditResourceType.TRADE)
                .resourceId(UUID.randomUUID())
                .requestId("req-path-2")
                .requestMethod("POST")
                .requestPath("/api/v1/trades/sell")
                .build());

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .param("requestPath", "/api/v1/trades/buy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.requestPath").value("/api/v1/trades/buy"))
                .andExpect(jsonPath("$.entries[0].requestPath").value("/api/v1/trades/buy"));
    }

    @Test
    void auditOpsEndpoint_shouldApplyDateWindowFilter() throws Exception {
        AuditLogEntry recent = auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.PORTFOLIO_CREATED)
                .resourceType(AuditResourceType.PORTFOLIO)
                .resourceId(UUID.randomUUID())
                .requestId("req-recent")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .build());
        AuditLogEntry old = auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.PORTFOLIO_CREATED)
                .resourceType(AuditResourceType.PORTFOLIO)
                .resourceId(UUID.randomUUID())
                .requestId("req-old")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .build());

        jdbcTemplate.update(
                "update audit_logs set created_at = ? where id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusHours(6)),
                recent.getId());
        jdbcTemplate.update(
                "update audit_logs set created_at = ? where id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(5)),
                old.getId());

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].requestId").value("req-recent"));
    }

    @Test
    void auditOpsExport_shouldReturnCsvForAppliedFilters() throws Exception {
        UUID actorId = UUID.randomUUID();
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(actorId)
                .actionType(AuditActionType.TRADE_BUY_EXECUTED)
                .resourceType(AuditResourceType.TRADE)
                .resourceId(UUID.randomUUID())
                .requestId("req-export-audit")
                .requestMethod("POST")
                .requestPath("/api/v1/trades/buy")
                .build());

        mockMvc.perform(get("/api/v1/ops/auditlog/export")
                        .param("actorId", actorId.toString())
                        .param("actionType", "TRADE_BUY_EXECUTED")
                        .param("resourceType", "TRADE"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TRADE_BUY_EXECUTED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("req-export-audit")));
    }

    @Test
    void auditOpsExport_shouldReturnJsonForAppliedFiltersAndPageState() throws Exception {
        UUID actorId = UUID.randomUUID();
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(actorId)
                .actionType(AuditActionType.TRADE_BUY_EXECUTED)
                .resourceType(AuditResourceType.TRADE)
                .resourceId(UUID.randomUUID())
                .requestId("req-export-json")
                .requestMethod("POST")
                .requestPath("/api/v1/trades/buy")
                .build());

        mockMvc.perform(get("/api/v1/ops/auditlog/export/json")
                        .param("limit", "10")
                        .param("page", "0")
                        .param("actorId", actorId.toString())
                        .param("actionType", "TRADE_BUY_EXECUTED")
                        .param("resourceType", "TRADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filters.limit").value(10))
                .andExpect(jsonPath("$.filters.page").value(0))
                .andExpect(jsonPath("$.filters.requestPath").value(""))
                .andExpect(jsonPath("$.snapshot.actorId").value(actorId.toString()))
                .andExpect(jsonPath("$.snapshot.entries[0].requestId").value("req-export-json"));
    }

    @Test
    void auditOpsEndpoint_shouldPaginateResults() throws Exception {
        for (int i = 0; i < 3; i++) {
            auditLogRepository.save(AuditLogEntry.builder()
                    .actorId(UUID.randomUUID())
                    .actionType(AuditActionType.PORTFOLIO_CREATED)
                    .resourceType(AuditResourceType.PORTFOLIO)
                    .resourceId(UUID.randomUUID())
                    .requestId("req-page-" + i)
                    .requestMethod("POST")
                    .requestPath("/api/v1/portfolios")
                    .build());
        }

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .param("limit", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.facets.actions[0].count").value(3))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .param("limit", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void auditOpsEndpoint_whenInspectionFails_ShouldReturnCorrelatedApiError() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(inspectionService)
                .snapshot(nullable(Integer.class), nullable(Integer.class), nullable(Integer.class), nullable(String.class),
                        nullable(String.class), nullable(UUID.class), nullable(AuditActionType.class), nullable(AuditResourceType.class));

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .header("X-Request-Id", "audit-err-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "audit-err-1"))
                .andExpect(jsonPath("$.code").value("audit_snapshot_failed"))
                .andExpect(jsonPath("$.message").value("Failed to inspect audit log"))
                .andExpect(jsonPath("$.requestId").value("audit-err-1"));
    }

    @Test
    void auditOpsExport_whenCsvExportFails_ShouldReturnCorrelatedApiError() throws Exception {
        doThrow(new RuntimeException("csv-boom"))
                .when(inspectionService)
                .exportCsv(nullable(Integer.class), nullable(Integer.class), nullable(String.class), nullable(String.class),
                        nullable(UUID.class), nullable(AuditActionType.class), nullable(AuditResourceType.class));

        mockMvc.perform(get("/api/v1/ops/auditlog/export")
                        .header("X-Request-Id", "audit-csv-err-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("X-Request-Id", "audit-csv-err-1"))
                .andExpect(jsonPath("$.code").value("audit_export_failed"))
                .andExpect(jsonPath("$.message").value("Failed to export audit log"))
                .andExpect(jsonPath("$.requestId").value("audit-csv-err-1"));
    }

    @Test
    void auditOpsExport_whenJsonExportFails_ShouldReturnCorrelatedApiError() throws Exception {
        doThrow(new RuntimeException("json-boom"))
                .when(inspectionService)
                .exportJson(nullable(Integer.class), nullable(Integer.class), nullable(Integer.class), nullable(String.class),
                        nullable(String.class), nullable(UUID.class), nullable(AuditActionType.class), nullable(AuditResourceType.class));

        mockMvc.perform(get("/api/v1/ops/auditlog/export/json")
                        .header("X-Request-Id", "audit-json-err-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "audit-json-err-1"))
                .andExpect(jsonPath("$.code").value("audit_export_json_failed"))
                .andExpect(jsonPath("$.message").value("Failed to export audit log view"))
                .andExpect(jsonPath("$.requestId").value("audit-json-err-1"));
    }
}
