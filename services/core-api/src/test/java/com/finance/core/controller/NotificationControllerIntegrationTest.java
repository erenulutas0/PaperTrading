package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Notification;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.repository.*;
import com.finance.core.service.BinanceService;
import com.finance.core.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private NotificationRepository notificationRepository;

        @Autowired
        private FollowRepository followRepository;

        @Autowired
        private PortfolioRepository portfolioRepository;

        @Autowired
        private InteractionRepository interactionRepository;

        @MockitoBean
        private BinanceService binanceService;

        @Autowired
        private JwtTokenService jwtTokenService;

        private AppUser userA;
        private AppUser userB;

        @BeforeEach
        void setUp() {
                interactionRepository.deleteAll();
                notificationRepository.deleteAll();
                followRepository.deleteAll();
                portfolioRepository.deleteAll();
                userRepository.deleteAll();

                userA = userRepository.save(AppUser.builder()
                                .username("user_a")
                                .email("a@test.com")
                                .password("pass")
                                .build());

                userB = userRepository.save(AppUser.builder()
                                .username("user_b")
                                .email("b@test.com")
                                .password("pass")
                                .build());
        }

        @Test
        void followUser_ShouldGenerateNotification() throws Exception {
                // User A follows User B
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userB.getId())
                                .header("X-User-Id", userA.getId().toString()))
                                .andExpect(status().isOk());

                // Wait for async notification processing
                await().atMost(2, TimeUnit.SECONDS)
                                .until(() -> notificationRepository.countByUserIdAndReadFalse(userB.getId()) == 1);

                // Verify notification content
                mockMvc.perform(get("/api/v1/notifications")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].type").value("FOLLOW"))
                                .andExpect(jsonPath("$.content[0].actorUsername").value("user_a"));
        }

        @Test
        void likePortfolio_ShouldGenerateNotification() throws Exception {
                // User B creates a portfolio
                Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                                .name("B's Gems")
                                .ownerId(userB.getId().toString())
                                .visibility(Portfolio.Visibility.PUBLIC)
                                .build());

                // User A likes it
                InteractionRequest request = new InteractionRequest();
                request.setTargetType("PORTFOLIO");

                mockMvc.perform(post("/api/v1/interactions/{targetId}/like", portfolio.getId())
                                .header("X-User-Id", userA.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                await().atMost(2, TimeUnit.SECONDS)
                                .until(() -> notificationRepository.countByUserIdAndReadFalse(userB.getId()) == 1);

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(1));
        }

        @Test
        void selfAction_ShouldNotGenerateNotification() throws Exception {
                // User A creates a portfolio
                Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                                .name("A's Own Portfolio")
                                .ownerId(userA.getId().toString())
                                .visibility(Portfolio.Visibility.PUBLIC)
                                .build());

                // User A likes their OWN portfolio
                InteractionRequest request = new InteractionRequest();
                request.setTargetType("PORTFOLIO");

                mockMvc.perform(post("/api/v1/interactions/{targetId}/like", portfolio.getId())
                                .header("X-User-Id", userA.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                // No notification should be generated for self-action
                Thread.sleep(500); // Give it a bit of time
                assert notificationRepository.countByUserIdAndReadFalse(userA.getId()) == 0;
        }

        @Test
        void commentAction_ShouldGenerateNotification() throws Exception {
                Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                                .name("B's Gems")
                                .ownerId(userB.getId().toString())
                                .visibility(Portfolio.Visibility.PUBLIC)
                                .build());

                InteractionRequest request = new InteractionRequest();
                request.setTargetType("PORTFOLIO");
                request.setContent("Great portfolio!");

                mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                                .header("X-User-Id", userA.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                await().atMost(2, TimeUnit.SECONDS)
                                .until(() -> notificationRepository.countByUserIdAndReadFalse(userB.getId()) == 1);

                mockMvc.perform(get("/api/v1/notifications")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(jsonPath("$.content[0].type").value("PORTFOLIO_COMMENT"))
                                .andExpect(jsonPath("$.content[0].actorUsername").value("user_a"));
        }

        @Test
        void commentLikeAndReply_ShouldGenerateCommentSpecificNotifications() throws Exception {
                Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                                .name("B's Gems")
                                .ownerId(userB.getId().toString())
                                .visibility(Portfolio.Visibility.PUBLIC)
                                .build());

                InteractionRequest rootComment = new InteractionRequest();
                rootComment.setTargetType("PORTFOLIO");
                rootComment.setContent("Root comment");

                String rootCommentBody = mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", portfolio.getId())
                                .header("X-User-Id", userB.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(rootComment)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                String commentId = objectMapper.readTree(rootCommentBody).get("id").asText();

                InteractionRequest likeRequest = new InteractionRequest();
                likeRequest.setTargetType("COMMENT");

                mockMvc.perform(post("/api/v1/interactions/{targetId}/like", commentId)
                                .header("X-User-Id", userA.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(likeRequest)))
                                .andExpect(status().isOk());

                InteractionRequest replyRequest = new InteractionRequest();
                replyRequest.setTargetType("COMMENT");
                replyRequest.setContent("Reply to your comment");

                mockMvc.perform(post("/api/v1/interactions/{targetId}/comments", commentId)
                                .header("X-User-Id", userA.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(replyRequest)))
                                .andExpect(status().isOk());

                await().atMost(2, TimeUnit.SECONDS)
                                .until(() -> notificationRepository.countByUserIdAndReadFalse(userB.getId()) >= 2);

                mockMvc.perform(get("/api/v1/notifications")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[*].type", hasItems("PORTFOLIO_COMMENT_LIKE", "PORTFOLIO_COMMENT_REPLY")))
                                .andExpect(jsonPath("$.content[*].actorUsername", hasItem("user_a")));
        }

        @Test
        void pagination_ShouldWorkCorrectly() throws Exception {
                // Generate 25 notifications for User B
                for (int i = 0; i < 25; i++) {
                        notificationRepository.save(Notification.builder()
                                        .userId(userB.getId())
                                        .actorUsername("actor_" + i)
                                        .type(Notification.NotificationType.FOLLOW)
                                        .read(false)
                                        .build());
                }

                // Test first page (default size 20)
                mockMvc.perform(get("/api/v1/notifications")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.page.size").value(20))
                                .andExpect(jsonPath("$.page.totalElements").value(25))
                                .andExpect(jsonPath("$.content", hasSize(20)));

                // Test second page
                mockMvc.perform(get("/api/v1/notifications?page=1")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(5)));
        }

        @Test
        void markRead_ShouldResetUnreadCount() throws Exception {
                // Premade notification
                notificationRepository.save(Notification.builder()
                                .userId(userB.getId())
                                .actorId(userA.getId())
                                .actorUsername("user_a")
                                .type(Notification.NotificationType.FOLLOW)
                                .read(false)
                                .build());

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(jsonPath("$.count").value(1));

                mockMvc.perform(post("/api/v1/notifications/mark-read")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(jsonPath("$.count").value(0));
        }

        @Test
        void priceAlertType_ShouldPersistAndBeCounted() throws Exception {
                notificationRepository.saveAndFlush(Notification.builder()
                                .userId(userB.getId())
                                .type(Notification.NotificationType.PRICE_ALERT)
                                .read(false)
                                .build());

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(1));
        }

        @Test
        void markSingleRead_ShouldOnlyMarkOwnedNotification() throws Exception {
                Notification target = notificationRepository.saveAndFlush(Notification.builder()
                                .userId(userB.getId())
                                .actorId(userA.getId())
                                .actorUsername("user_a")
                                .type(Notification.NotificationType.FOLLOW)
                                .read(false)
                                .build());

                assertNotNull(target.getId());

                mockMvc.perform(post("/api/v1/notifications/{notificationId}/mark-read", target.getId())
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk());

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("X-User-Id", userB.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(0));

                mockMvc.perform(post("/api/v1/notifications/{notificationId}/mark-read", target.getId())
                                .header("X-Request-Id", "notif-err-1")
                                .header("X-User-Id", userA.getId().toString()))
                                .andExpect(status().isNotFound())
                                .andExpect(header().string("X-Request-Id", "notif-err-1"))
                                .andExpect(jsonPath("$.code").value("notification_not_found"))
                                .andExpect(jsonPath("$.message").value("Notification not found"))
                                .andExpect(jsonPath("$.requestId").value("notif-err-1"));
        }

        @Test
        void unreadCount_WithBearerOnly_ShouldWork() throws Exception {
                String token = jwtTokenService.generateAccessToken(userB.getId(), userB.getUsername());

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.count").value(0));
        }

        @Test
        void unreadCount_WithBearerAndMismatchedLegacyHeader_ShouldReturnUnauthorized() throws Exception {
                String token = jwtTokenService.generateAccessToken(userB.getId(), userB.getUsername());

                mockMvc.perform(get("/api/v1/notifications/unread-count")
                                .header("Authorization", "Bearer " + token)
                                .header("X-User-Id", userA.getId().toString()))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void streamToken_WithBearerOnly_ShouldReturnShortLivedStreamToken() throws Exception {
                String token = jwtTokenService.generateAccessToken(userB.getId(), userB.getUsername());

                mockMvc.perform(get("/api/v1/notifications/stream-token")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.streamToken", not(blankOrNullString())))
                                .andExpect(jsonPath("$.expiresInSeconds").isNumber());
        }

        @Test
        void subscribe_WithInvalidStreamToken_ShouldReturnBadRequest() throws Exception {
                mockMvc.perform(get("/api/v1/notifications/stream")
                                .param("streamToken", "invalid-token"))
                                .andExpect(status().isBadRequest());
        }
}
