package com.finance.core.observability;

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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void auditLogEndpoint_shouldExposeRecentEntriesAndRequestIdFilter() throws Exception {
        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.USER_FOLLOWED)
                .resourceType(AuditResourceType.USER)
                .resourceId(UUID.randomUUID())
                .requestId("req-audit-1")
                .requestMethod("POST")
                .requestPath("/api/v1/users/x/follow")
                .details("{\"targetUserId\":\"u-1\"}")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build());

        auditLogRepository.save(AuditLogEntry.builder()
                .actorId(UUID.randomUUID())
                .actionType(AuditActionType.PORTFOLIO_CREATED)
                .resourceType(AuditResourceType.PORTFOLIO)
                .resourceId(UUID.randomUUID())
                .requestId("req-audit-2")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .details("{\"name\":\"Smoke\"}")
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/actuator/auditlog")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].requestId").value("req-audit-2"))
                .andExpect(jsonPath("$.entries[0].details.name").value("Smoke"));

        mockMvc.perform(get("/actuator/auditlog")
                        .param("requestId", "req-audit-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.entries[0].requestId").value("req-audit-1"))
                .andExpect(jsonPath("$.entries[0].actionType").value("USER_FOLLOWED"));

        mockMvc.perform(get("/actuator/auditlog")
                        .param("requestPath", "/api/v1/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.requestPath").value("/api/v1/portfolios"))
                .andExpect(jsonPath("$.entries[0].requestPath").value("/api/v1/portfolios"));
    }
}
