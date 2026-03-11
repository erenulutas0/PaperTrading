package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.repository.ActivityEventRepository;
import com.finance.core.repository.FollowRepository;
import com.finance.core.repository.InteractionRepository;
import com.finance.core.repository.NotificationRepository;
import com.finance.core.repository.PortfolioRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private InteractionRepository interactionRepository;

    @Autowired
    private ActivityEventRepository activityEventRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private BinanceService binanceService;

    private AppUser owner;
    private AppUser actor;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        activityEventRepository.deleteAll();
        interactionRepository.deleteAll();
        notificationRepository.deleteAll();
        followRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(AppUser.builder()
                .username("owner_" + UUID.randomUUID().toString().substring(0, 6))
                .email("owner_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());

        actor = userRepository.save(AppUser.builder()
                .username("actor_" + UUID.randomUUID().toString().substring(0, 6))
                .email("actor_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .password("pass")
                .build());

        portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Scale Portfolio")
                .ownerId(owner.getId().toString())
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        org.mockito.Mockito.when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
    }

    @Test
    void likePortfolio_shouldCreateFeedEventAndLikeState() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", portfolio.getId())
                .header("X-User-Id", actor.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/feed/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.eventType == 'PORTFOLIO_LIKED')]").exists());

        mockMvc.perform(get("/api/v1/interactions/{targetId}/likes/count", portfolio.getId())
                .param("type", "PORTFOLIO")
                .header("X-User-Id", actor.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.hasLiked").value(true));

        mockMvc.perform(get("/api/v1/interactions/{targetId}/summary", portfolio.getId())
                .param("type", "PORTFOLIO")
                .header("X-User-Id", actor.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.hasLiked").value(true))
                .andExpect(jsonPath("$.commentCount").value(0));
    }

    @Test
    void commentPortfolio_shouldCreateFeedEventAndComment() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");
        request.setContent("Clean execution.");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                .header("X-User-Id", actor.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/feed/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.eventType == 'PORTFOLIO_COMMENTED')]").exists());

        mockMvc.perform(get("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                .param("type", "PORTFOLIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Clean execution."))
                .andExpect(jsonPath("$.content[0].actorUsername", notNullValue()))
                .andExpect(jsonPath("$.content[0].replyCount").value(0));

        mockMvc.perform(get("/api/v1/interactions/{targetId}/summary", portfolio.getId())
                .param("type", "PORTFOLIO")
                .header("X-User-Id", actor.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.hasLiked").value(false));
    }

    @Test
    void replyAndLikeComment_shouldSupportCommentThreads() throws Exception {
        InteractionRequest rootCommentRequest = new InteractionRequest();
        rootCommentRequest.setTargetType("PORTFOLIO");
        rootCommentRequest.setContent("Root comment.");

        String rootCommentBody = mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                        .header("X-User-Id", actor.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rootCommentRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rootCommentId = objectMapper.readTree(rootCommentBody).get("id").asText();

        InteractionRequest replyRequest = new InteractionRequest();
        replyRequest.setTargetType("COMMENT");
        replyRequest.setContent("Nested reply.");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", rootCommentId)
                .header("X-User-Id", owner.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(replyRequest)))
                .andExpect(status().isOk());

        InteractionRequest likeCommentRequest = new InteractionRequest();
        likeCommentRequest.setTargetType("COMMENT");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", rootCommentId)
                .header("X-User-Id", owner.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(likeCommentRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                .param("type", "PORTFOLIO")
                .header("X-User-Id", owner.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].replyCount").value(1))
                .andExpect(jsonPath("$.content[0].likeCount").value(1))
                .andExpect(jsonPath("$.content[0].hasLiked").value(true));

        mockMvc.perform(get("/api/v1/interactions/{targetId}/comments", rootCommentId)
                .param("type", "COMMENT")
                .header("X-User-Id", owner.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Nested reply."));
    }

    @Test
    void likeMissingPortfolio_shouldReturn404() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", UUID.randomUUID())
                .header("X-User-Id", actor.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("Portfolio not found")));
    }
}
