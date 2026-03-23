package com.finance.core.observability;

import com.finance.core.domain.IdempotencyKeyRecord;
import com.finance.core.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.show-components=always",
        "app.idempotency.cleanup-interval=PT1M"
})
@AutoConfigureMockMvc
class IdempotencyHealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private IdempotencyObservabilityService observabilityService;

    @BeforeEach
    void setUp() {
        observabilityService.resetRuntimeTelemetry();
        idempotencyKeyRepository.deleteAll();
    }

    @Test
    void shouldExposeHealthyIdempotencyHealthComponent() throws Exception {
        observabilityService.recordClaimed();
        observabilityService.recordCompletedResponse();

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.idempotency.status").value("UP"))
                .andExpect(jsonPath("$.components.idempotency.details.status").value("healthy"))
                .andExpect(jsonPath("$.components.idempotency.details.cleanupIntervalSeconds").value(60))
                .andExpect(jsonPath("$.components.idempotency.details.claimedCount").value(1))
                .andExpect(jsonPath("$.components.idempotency.details.completedResponseCount").value(1))
                .andExpect(jsonPath("$.components.idempotency.details.oldestExpiredAgeSeconds").value(""));

        mockMvc.perform(get("/actuator/health/idempotency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.status").value("healthy"))
                .andExpect(jsonPath("$.details.cleanupIntervalSeconds").value(60));
    }

    @Test
    void shouldMarkIdempotencyHealthDownWhenExpiredBacklogIsStale() throws Exception {
        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("health-user")
                .idempotencyKey("stale-expired-key")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .requestHash("hash-health")
                .status(IdempotencyKeyRecord.Status.COMPLETED)
                .responseStatus(200)
                .responseBody("{}")
                .completedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().minusMinutes(3))
                .build());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.idempotency.status").value("DOWN"))
                .andExpect(jsonPath("$.components.idempotency.details.status").value("cleanup-lagging"))
                .andExpect(jsonPath("$.components.idempotency.details.expiredRecords").value(1))
                .andExpect(jsonPath("$.components.idempotency.details.oldestExpiredAgeSeconds").isNumber());

        mockMvc.perform(get("/actuator/health/idempotency"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.details.status").value("cleanup-lagging"))
                .andExpect(jsonPath("$.details.expiredRecords").value(1));
    }
}
