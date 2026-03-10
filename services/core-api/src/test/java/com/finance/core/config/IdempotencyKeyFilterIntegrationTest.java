package com.finance.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.repository.FollowRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyKeyFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        followRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(AppUser.builder()
                .username("idem_a_" + UUID.randomUUID().toString().substring(0, 6))
                .email("idem_a_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());

        userB = userRepository.save(AppUser.builder()
                .username("idem_b_" + UUID.randomUUID().toString().substring(0, 6))
                .email("idem_b_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());
    }

    @Test
    void createPortfolio_sameKeySamePayload_shouldReplayStoredResponse() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "Replay Portfolio",
                "ownerId", userA.getId().toString(),
                "visibility", "PUBLIC");

        String firstBody = mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "portfolio-create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondBody = mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "portfolio-create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode first = objectMapper.readTree(firstBody);
        JsonNode second = objectMapper.readTree(secondBody);

        assertEquals(first.get("id").asText(), second.get("id").asText());
        assertEquals(1L, portfolioRepository.count());
    }

    @Test
    void createPortfolio_sameKeyDifferentPayload_shouldReturnConflict() throws Exception {
        Map<String, Object> firstRequest = Map.of(
                "name", "Replay Portfolio",
                "ownerId", userA.getId().toString(),
                "visibility", "PUBLIC");
        Map<String, Object> secondRequest = Map.of(
                "name", "Different Portfolio",
                "ownerId", userA.getId().toString(),
                "visibility", "PRIVATE");

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "portfolio-conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/portfolios")
                        .header("Idempotency-Key", "portfolio-conflict-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));
    }

    @Test
    void follow_sameKeySamePayload_shouldReplayAndAvoidDuplicateFollow() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/follow", userB.getId())
                        .header("Idempotency-Key", "follow-key")
                        .header("X-User-Id", userA.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/{userId}/follow", userB.getId())
                        .header("Idempotency-Key", "follow-key")
                        .header("X-User-Id", userA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"));

        assertEquals(1L, followRepository.count());
    }

    @Test
    void failedWrite_shouldReleaseKeySoFixedRetryCanProceed() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/follow", userA.getId())
                        .header("Idempotency-Key", "recoverable-key")
                        .header("X-User-Id", userA.getId().toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/users/{userId}/follow", userB.getId())
                        .header("Idempotency-Key", "recoverable-key")
                        .header("X-User-Id", userA.getId().toString()))
                .andExpect(status().isOk());

        assertEquals(1L, followRepository.count());
    }
}
