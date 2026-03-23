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
import com.finance.core.service.CacheService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActivityFeedControllerIntegrationTest {

    private static final String GLOBAL_FEED_CACHE_KEY = "feed:global:p0:s20:sort:unsorted";

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
    private CacheService cacheService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private BinanceService binanceService;

    private AppUser owner;
    private AppUser actor;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(redisAvailable(), "Redis is required for cache-hit integration test");

        cacheService.deletePattern("feed:*");
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
                .name("Cache Test Portfolio")
                .ownerId(owner.getId().toString())
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        org.mockito.Mockito.when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
    }

    @Test
    void globalFeed_secondReadShouldComeFromRedisCache() throws Exception {
        InteractionRequest request = new InteractionRequest();
        request.setTargetType("PORTFOLIO");

        mockMvc.perform(post("/api/v1/interactions/{targetId}/like", portfolio.getId())
                .header("X-User-Id", actor.getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/feed/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PORTFOLIO_LIKED"));

        assertTrue(cacheService.exists(GLOBAL_FEED_CACHE_KEY));

        activityEventRepository.deleteAll();

        mockMvc.perform(get("/api/v1/feed/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PORTFOLIO_LIKED"));
    }

    @Test
    void globalFeed_withInvalidPage_shouldReturnExplicitBadRequestContract() throws Exception {
        mockMvc.perform(get("/api/v1/feed/global")
                        .param("page", "later")
                        .header("X-Request-Id", "feed-page-err-1"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "feed-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_feed_page"))
                .andExpect(jsonPath("$.message").value("Invalid feed page"))
                .andExpect(jsonPath("$.requestId").value("feed-page-err-1"));
    }

    @Test
    void personalizedFeed_withInvalidSize_shouldReturnExplicitBadRequestContract() throws Exception {
        mockMvc.perform(get("/api/v1/feed")
                        .header("X-User-Id", owner.getId().toString())
                        .param("size", "0")
                        .header("X-Request-Id", "feed-page-err-2"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "feed-page-err-2"))
                .andExpect(jsonPath("$.code").value("invalid_feed_size"))
                .andExpect(jsonPath("$.message").value("Invalid feed size"))
                .andExpect(jsonPath("$.requestId").value("feed-page-err-2"));
    }

    @Test
    void personalizedFeed_unknownUser_shouldReturnExplicitNotFoundContract() throws Exception {
        mockMvc.perform(get("/api/v1/feed")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Request-Id", "feed-user-err-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "feed-user-err-1"))
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("feed-user-err-1"));
    }

    @Test
    void userActivity_unknownUser_shouldReturnExplicitNotFoundContract() throws Exception {
        mockMvc.perform(get("/api/v1/feed/user/" + UUID.randomUUID())
                        .header("X-Request-Id", "feed-activity-err-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "feed-activity-err-1"))
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("feed-activity-err-1"));
    }

    private boolean redisAvailable() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return connection.ping() != null;
        } catch (Exception e) {
            return false;
        }
    }
}
