package com.finance.core.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.IdempotencyKeyRecord;
import com.finance.core.repository.IdempotencyKeyRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private IdempotencyObservabilityService observabilityService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private UserRepository userRepository;

    private AppUser owner;

    @BeforeEach
    void setUp() {
        observabilityService.resetRuntimeTelemetry();
        idempotencyKeyRepository.deleteAll();
        portfolioRepository.deleteAll();

        owner = userRepository.save(AppUser.builder()
                .username("idem_obs_" + UUID.randomUUID().toString().substring(0, 6))
                .email("idem_obs_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());
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
                .andExpect(jsonPath("$.claimedCount").value(0))
                .andExpect(jsonPath("$.replayCount").value(0))
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andExpect(jsonPath("$.inProgressConflictCount").value(0))
                .andExpect(jsonPath("$.completedResponseCount").value(0))
                .andExpect(jsonPath("$.releasedCount").value(0))
                .andExpect(jsonPath("$.skippedLargeResponseCount").value(0))
                .andExpect(jsonPath("$.alertState").value("NONE"))
                .andExpect(jsonPath("$.ttlSeconds").isNumber())
                .andExpect(jsonPath("$.cleanupIntervalSeconds").isNumber());
    }

    @Test
    void idempotencyEndpoint_shouldExposeRuntimeOutcomeTelemetry() throws Exception {
        Map<String, Object> validRequest = Map.of(
                "name", "Telemetry Portfolio",
                "ownerId", owner.getId().toString(),
                "visibility", "PUBLIC");
        Map<String, Object> conflictRequest = Map.of(
                "name", "Telemetry Portfolio Conflict",
                "ownerId", owner.getId().toString(),
                "visibility", "PRIVATE");
        Map<String, Object> invalidRequest = Map.of(
                "ownerId", owner.getId().toString(),
                "visibility", "PUBLIC");

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "obs-runtime-key")
                        .header("X-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "obs-runtime-key")
                        .header("X-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "obs-runtime-key")
                        .header("X-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictRequest)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "obs-release-key")
                        .header("X-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/actuator/idempotency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").value(2))
                .andExpect(jsonPath("$.replayCount").value(1))
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.inProgressConflictCount").value(0))
                .andExpect(jsonPath("$.completedResponseCount").value(1))
                .andExpect(jsonPath("$.releasedCount").value(1))
                .andExpect(jsonPath("$.skippedLargeResponseCount").value(0))
                .andExpect(jsonPath("$.alertState").value("NONE"))
                .andExpect(jsonPath("$.lastClaimedAt").isNotEmpty())
                .andExpect(jsonPath("$.lastReplayAt").isNotEmpty())
                .andExpect(jsonPath("$.lastConflictAt").isNotEmpty())
                .andExpect(jsonPath("$.lastCompletedResponseAt").isNotEmpty())
                .andExpect(jsonPath("$.lastReleasedAt").isNotEmpty());
    }

    @Test
    void idempotencyEndpoint_shouldExposeInProgressConflictTelemetry() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "Pending Portfolio",
                "ownerId", owner.getId().toString(),
                "visibility", "PUBLIC");
        String requestBody = objectMapper.writeValueAsString(request);

        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope(owner.getId().toString())
                .idempotencyKey("obs-in-progress-key")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .requestHash(sha256(requestBody))
                .status(IdempotencyKeyRecord.Status.IN_PROGRESS)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build());

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "obs-in-progress-key")
                        .header("X-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_request_in_progress"));

        mockMvc.perform(get("/actuator/idempotency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedCount").value(0))
                .andExpect(jsonPath("$.inProgressConflictCount").value(1))
                .andExpect(jsonPath("$.alertState").value("NONE"))
                .andExpect(jsonPath("$.lastInProgressConflictAt").isNotEmpty());
    }

    @Test
    void idempotencyEndpoint_shouldExposeWarningAlertStateWhenExpiredBacklogIsStale() throws Exception {
        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("user-stale")
                .idempotencyKey("key-stale-alert")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .requestHash("hash-stale")
                .status(IdempotencyKeyRecord.Status.COMPLETED)
                .responseStatus(200)
                .responseBody("{}")
                .completedAt(LocalDateTime.now().minusHours(4))
                .expiresAt(LocalDateTime.now().minusHours(2))
                .build());

        mockMvc.perform(get("/actuator/idempotency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredRecords").value(1))
                .andExpect(jsonPath("$.alertState").value("WARNING"))
                .andExpect(jsonPath("$.oldestExpiredAgeSeconds").isNumber());
    }

    @Test
    void idempotencyEndpoint_cleanupWriteOperationShouldPurgeExpiredRecords() throws Exception {
        LocalDateTime now = LocalDateTime.now();

        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("user-live")
                .idempotencyKey("key-live")
                .requestMethod("POST")
                .requestPath("/api/v1/portfolios")
                .requestHash("hash-live")
                .status(IdempotencyKeyRecord.Status.COMPLETED)
                .responseStatus(200)
                .responseBody("{}")
                .completedAt(now.minusMinutes(1))
                .expiresAt(now.plusHours(1))
                .build());

        idempotencyKeyRepository.save(IdempotencyKeyRecord.builder()
                .actorScope("user-expired")
                .idempotencyKey("key-expired-cleanup")
                .requestMethod("POST")
                .requestPath("/api/v1/interactions/x/comments")
                .requestHash("hash-expired")
                .status(IdempotencyKeyRecord.Status.COMPLETED)
                .responseStatus(200)
                .responseBody("{}")
                .completedAt(now.minusHours(2))
                .expiresAt(now.minusMinutes(2))
                .build());

        mockMvc.perform(post("/actuator/idempotency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecords").value(1))
                .andExpect(jsonPath("$.completedRecords").value(1))
                .andExpect(jsonPath("$.expiredRecords").value(0))
                .andExpect(jsonPath("$.alertState").value("NONE"))
                .andExpect(jsonPath("$.lastCleanupDeletedCount").value(1))
                .andExpect(jsonPath("$.lastCleanupAt").isNotEmpty());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
