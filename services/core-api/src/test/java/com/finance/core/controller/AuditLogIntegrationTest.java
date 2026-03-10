package com.finance.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditLogEntry;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.dto.TradeRequest;
import com.finance.core.repository.ActivityEventRepository;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.AuditLogRepository;
import com.finance.core.repository.FollowRepository;
import com.finance.core.repository.InteractionRepository;
import com.finance.core.repository.NotificationRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeActivityRepository tradeActivityRepository;

    @Autowired
    private AnalysisPostRepository analysisPostRepository;

    @Autowired
    private InteractionRepository interactionRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private BinanceService binanceService;

    private AppUser owner;
    private AppUser otherUser;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        followRepository.deleteAll();
        interactionRepository.deleteAll();
        activityEventRepository.deleteAll();
        analysisPostRepository.deleteAll();
        tradeActivityRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(AppUser.builder()
                .username("owner_" + UUID.randomUUID().toString().substring(0, 6))
                .email("owner_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());

        otherUser = userRepository.save(AppUser.builder()
                .username("other_" + UUID.randomUUID().toString().substring(0, 6))
                .email("other_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());

        portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Audit Portfolio")
                .ownerId(owner.getId().toString())
                .visibility(Portfolio.Visibility.PUBLIC)
                .balance(BigDecimal.valueOf(10000))
                .build());

        org.mockito.Mockito.when(binanceService.getPrices()).thenReturn(Map.of(
                "BTCUSDT", 50000.0,
                "ETHUSDT", 3000.0));
    }

    @Test
    void tradeBuy_shouldPersistAuditLogWithRequestContext() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(portfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(2);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "req-trade-audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        AuditLogEntry entry = auditLogRepository.findAll().stream()
                .filter(log -> log.getActionType() == AuditActionType.TRADE_BUY_EXECUTED)
                .findFirst()
                .orElseThrow();

        assertEquals(owner.getId(), entry.getActorId());
        assertEquals("req-trade-audit", entry.getRequestId());
        assertEquals("/api/v1/trade/buy", entry.getRequestPath());
        assertNotNull(entry.getResourceId());

        JsonNode details = objectMapper.readTree(entry.getDetails());
        assertEquals(portfolio.getId().toString(), details.get("portfolioId").asText());
        assertEquals("BTCUSDT", details.get("symbol").asText());
    }

    @Test
    void follow_shouldPersistAuditLog() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/follow", otherUser.getId())
                        .header("X-Request-Id", "req-follow-audit")
                        .header("X-User-Id", owner.getId().toString()))
                .andExpect(status().isOk());

        AuditLogEntry entry = auditLogRepository.findAll().stream()
                .filter(log -> log.getActionType() == AuditActionType.USER_FOLLOWED)
                .findFirst()
                .orElseThrow();

        assertEquals(owner.getId(), entry.getActorId());
        assertEquals(otherUser.getId(), entry.getResourceId());
        assertEquals("req-follow-audit", entry.getRequestId());
    }

    @Test
    void analysisCreateAndDelete_shouldPersistAuditLogs() throws Exception {
        AnalysisPostRequest request = AnalysisPostRequest.builder()
                .title("Audit BTC")
                .content("Audit trail check")
                .instrumentSymbol("BTCUSDT")
                .direction("BULLISH")
                .targetPrice(BigDecimal.valueOf(55000))
                .build();

        String body = mockMvc.perform(post("/api/v1/analysis-posts")
                        .header("X-Request-Id", "req-post-create")
                        .header("X-User-Id", owner.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID postId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                        .header("X-Request-Id", "req-post-delete")
                        .header("X-User-Id", owner.getId().toString()))
                .andExpect(status().isOk());

        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(log -> log.getActionType() == AuditActionType.ANALYSIS_POST_CREATED
                        && postId.equals(log.getResourceId())
                        && "req-post-create".equals(log.getRequestId())));

        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(log -> log.getActionType() == AuditActionType.ANALYSIS_POST_DELETED
                        && postId.equals(log.getResourceId())
                        && "req-post-delete".equals(log.getRequestId())));
    }

    @Test
    void comment_shouldPersistAuditLog() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");
        request.setContent("Audit comment");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                        .header("X-Request-Id", "req-comment-audit")
                        .header("X-User-Id", otherUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        AuditLogEntry entry = auditLogRepository.findAll().stream()
                .filter(log -> log.getActionType() == AuditActionType.INTERACTION_COMMENTED)
                .findFirst()
                .orElseThrow();

        assertEquals(otherUser.getId(), entry.getActorId());
        assertEquals(portfolio.getId(), entry.getResourceId());
        JsonNode details = objectMapper.readTree(entry.getDetails());
        assertEquals("PORTFOLIO", details.get("targetType").asText());
        assertEquals("Audit comment", details.get("contentPreview").asText());
    }
}
