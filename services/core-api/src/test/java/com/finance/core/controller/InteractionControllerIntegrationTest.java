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
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.BinanceService;
import com.finance.core.service.InteractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Autowired
    private StrategyBotRepository strategyBotRepository;

    @Autowired
    private StrategyBotRunRepository strategyBotRunRepository;

    @Autowired
    private StrategyBotRunFillRepository strategyBotRunFillRepository;

    @Autowired
    private StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;

    @MockitoBean
    private BinanceService binanceService;

    @SpyBean
    private InteractionService interactionService;

    private AppUser owner;
    private AppUser actor;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        activityEventRepository.deleteAll();
        interactionRepository.deleteAll();
        notificationRepository.deleteAll();
        followRepository.deleteAll();
        strategyBotRunEquityPointRepository.deleteAll();
        strategyBotRunFillRepository.deleteAll();
        strategyBotRunRepository.deleteAll();
        strategyBotRepository.deleteAll();
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
    void getComments_withInvalidSize_shouldReturnExplicitBadRequestContract() throws Exception {
        mockMvc.perform(get("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                .param("type", "PORTFOLIO")
                .param("size", "0")
                .header("X-Request-Id", "interaction-page-err-1"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "interaction-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_interaction_comments_size"))
                .andExpect(jsonPath("$.message").value("Invalid interaction comments size"))
                .andExpect(jsonPath("$.requestId").value("interaction-page-err-1"));
    }

    @Test
    void likeMissingPortfolio_shouldReturn404() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", UUID.randomUUID())
                .header("X-User-Id", actor.getId().toString())
                .header("X-Request-Id", "interaction-err-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("portfolio_not_found"))
                .andExpect(jsonPath("$.message").value("Portfolio not found"))
                .andExpect(jsonPath("$.requestId").value("interaction-err-1"));
    }

    @Test
    void addComment_withInvalidTargetType_shouldReturnUnifiedBadRequestContract() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("INVALID");
        request.setContent("Bad target");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                .header("X-User-Id", actor.getId().toString())
                .header("X-Request-Id", "interaction-err-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("interaction_target_type_invalid"))
                .andExpect(jsonPath("$.message").value("Invalid target type. Use PORTFOLIO, ANALYSIS_POST or COMMENT"))
                .andExpect(jsonPath("$.requestId").value("interaction-err-2"));
    }

    @Test
    void addComment_withEmptyContent_shouldReturnUnifiedBadRequestContract() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");
        request.setContent("   ");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                .header("X-User-Id", actor.getId().toString())
                .header("X-Request-Id", "interaction-err-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("interaction_comment_empty"))
                .andExpect(jsonPath("$.message").value("Comment content cannot be empty"))
                .andExpect(jsonPath("$.requestId").value("interaction-err-3"));
    }

    @Test
    void addComment_toMissingComment_shouldReturnUnifiedNotFoundContract() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("COMMENT");
        request.setContent("Reply");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", UUID.randomUUID())
                .header("X-User-Id", actor.getId().toString())
                .header("X-Request-Id", "interaction-err-4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("comment_not_found"))
                .andExpect(jsonPath("$.message").value("Comment not found"))
                .andExpect(jsonPath("$.requestId").value("interaction-err-4"));
    }

    @Test
    void likePortfolio_withUnknownActor_shouldReturnExplicitNotFoundContract() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", portfolio.getId())
                .header("X-User-Id", UUID.randomUUID().toString())
                .header("X-Request-Id", "interaction-err-5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "interaction-err-5"))
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("interaction-err-5"));
    }

    @Test
    void likePortfolio_withInvalidPortfolioOwner_shouldReturnExplicitBadRequestContract() throws Exception {
        Portfolio brokenPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Broken Portfolio")
                .ownerId("not-a-uuid")
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", brokenPortfolio.getId())
                        .header("X-User-Id", actor.getId().toString())
                        .header("X-Request-Id", "interaction-err-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "interaction-err-6"))
                .andExpect(jsonPath("$.code").value("portfolio_owner_invalid"))
                .andExpect(jsonPath("$.message").value("Portfolio owner is invalid"))
                .andExpect(jsonPath("$.requestId").value("interaction-err-6"));
    }

    @Test
    void likePortfolio_whenUnexpectedRuntimeOccurs_shouldReturnGenericFallbackMessage() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        doThrow(new RuntimeException("internal interaction details"))
                .when(interactionService)
                .toggleLike(eq(actor.getId()), eq(portfolio.getId()), any());

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", portfolio.getId())
                        .header("X-User-Id", actor.getId().toString())
                        .header("X-Request-Id", "interaction-fallback-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "interaction-fallback-1"))
                .andExpect(jsonPath("$.code").value("interaction_request_failed"))
                .andExpect(jsonPath("$.message").value("Interaction request failed"))
                .andExpect(jsonPath("$.requestId").value("interaction-fallback-1"));
    }
}
