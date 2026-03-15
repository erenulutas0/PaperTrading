package com.finance.core.controller;

import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditLogEntry;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditOpsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                .andExpect(jsonPath("$.entries[0].requestId").value("req-ops-audit"))
                .andExpect(jsonPath("$.entries[0].details.name").value("Ops Smoke"));
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
}
