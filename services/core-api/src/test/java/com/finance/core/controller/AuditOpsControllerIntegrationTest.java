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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditOpsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void auditOpsEndpoint_shouldExposeRecentAuditRows() throws Exception {
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.PORTFOLIO_CREATED)
                .resourceType(AuditResourceType.PORTFOLIO)
                .resourceId(UUID.randomUUID())
                .requestId("req-ops-audit")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .details("{\"name\":\"Ops Smoke\"}")
                .build());

        mockMvc.perform(get("/api/v1/ops/auditlog")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].requestId").value("req-ops-audit"))
                .andExpect(jsonPath("$.entries[0].details.name").value("Ops Smoke"));
    }
}
