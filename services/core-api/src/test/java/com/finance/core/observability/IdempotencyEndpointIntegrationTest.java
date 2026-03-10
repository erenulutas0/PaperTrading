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

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
    }

    @Test
    void idempotencyEndpoint_shouldExposeRecordCountsAndExpiredState() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("user-1")
                .idempotencyKey("key-in-progress")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .requestHash("hash-1")
                .status(IdempotencyKeyRecord.Status.IN_PROGRESS)
                .expiresAt(now.plusHours(1))
                .build());

        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("user-2")
                .idempotencyKey("key-completed")
                .requestMethod("POST")
                .requestPath("/api/v1/users/x/follow")
                .requestHash("hash-2")
                .status(IdempotencyKeyRecord.Status.COMPLETED)
                .responseStatus(200)
                .responseBody("{}")
                .completedAt(now.minusMinutes(1))
                .expiresAt(now.plusHours(1))
                .build());

        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("user-3")
                .idempotencyKey("key-expired")
                .requestMethod("POST")
                .requestPath("/api/v1/interactions/x/comments")
                .requestHash("hash-3")
                .status(IdempotencyKeyRecord.Status.COMPLETED)
                .responseStatus(200)
                .responseBody("{}")
                .completedAt(now.minusHours(2))
                .expiresAt(now.minusMinutes(5))
                .build());

        mockMvc.perform(get("/actuator/idempotency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.totalRecords").value(3))
                .andExpect(jsonPath("$.inProgressRecords").value(1))
                .andExpect(jsonPath("$.completedRecords").value(2))
                .andExpect(jsonPath("$.expiredRecords").value(1))
                .andExpect(jsonPath("$.ttlSeconds").isNumber())
                .andExpect(jsonPath("$.cleanupIntervalSeconds").isNumber());
    }
}
