package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.dto.UpdateProfileRequest;
import com.finance.core.repository.FollowRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private FollowRepository followRepository;

        @Autowired
        private PortfolioRepository portfolioRepository;

        @Autowired
        private com.finance.core.repository.ActivityEventRepository activityEventRepository;

        @Autowired
        private com.finance.core.repository.NotificationRepository notificationRepository;

        @Autowired
        private StrategyBotRepository strategyBotRepository;

        @Autowired
        private StrategyBotRunRepository strategyBotRunRepository;

        @Autowired
        private StrategyBotRunFillRepository strategyBotRunFillRepository;

        @Autowired
        private StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @org.springframework.test.context.bean.override.mockito.MockitoBean
        private BinanceService binanceService;

        private AppUser userAlice;
        private AppUser userBob;

        @BeforeEach
        void setUp() {
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));

                notificationRepository.deleteAll();
                activityEventRepository.deleteAll();
                followRepository.deleteAll();
                strategyBotRunEquityPointRepository.deleteAll();
                strategyBotRunFillRepository.deleteAll();
                strategyBotRunRepository.deleteAll();
                strategyBotRepository.deleteAll();
                portfolioRepository.deleteAll();
                userRepository.deleteAll();

                userAlice = userRepository.save(AppUser.builder()
                                .username("alice_" + UUID.randomUUID().toString().substring(0, 5))
                                .email("alice_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com")
                                .password("password123")
                                .displayName("Alice")
                                .bio("Crypto enthusiast")
                                .build());

                userBob = userRepository.save(AppUser.builder()
                                .username("bob_" + UUID.randomUUID().toString().substring(0, 5))
                                .email("bob_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com")
                                .password("password123")
                                .displayName("Bob")
                                .build());
        }

        // ===== GET PROFILE =====

        @Test
        void getProfile_returnsUserProfile() throws Exception {
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userAlice.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value(userAlice.getUsername()))
                                .andExpect(jsonPath("$.displayName").value("Alice"))
                                .andExpect(jsonPath("$.bio").value("Crypto enthusiast"))
                                .andExpect(jsonPath("$.followerCount").value(0))
                                .andExpect(jsonPath("$.followingCount").value(0))
                                .andExpect(jsonPath("$.following").value(false));
        }

        @Test
        void getProfile_withRequesterHeader_showsFollowingStatus() throws Exception {
                // Alice follows Bob first
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Now get Bob's profile as Alice — isFollowing should be true
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.following").value(true));
        }

        // ===== UPDATE PROFILE =====

        @Test
        void updateProfile_updatesFields() throws Exception {
                UpdateProfileRequest request = new UpdateProfileRequest();
                request.setDisplayName("Alice Pro ");
                request.setBio("Updated bio");

                mockMvc.perform(put("/api/v1/users/{userId}/profile", userAlice.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());

                // Verify the update
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userAlice.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.displayName").value("Alice Pro "))
                                .andExpect(jsonPath("$.bio").value("Updated bio"));
        }

        // ===== FOLLOW =====

        @Test
        void follow_createsRelationshipAndUpdatesCounters() throws Exception {
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Verify Bob's profile now shows 1 follower
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userBob.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.followerCount").value(1));

                // Verify Alice's profile shows 1 following
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userAlice.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.followingCount").value(1));
        }

        @Test
        void follow_self_returnsBadRequest() throws Exception {
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userAlice.getId())
                                .header("X-User-Id", userAlice.getId().toString())
                                .header("X-Request-Id", "follow-err-1"))
                                .andExpect(status().isBadRequest())
                                .andExpect(header().string("X-Request-Id", "follow-err-1"))
                                .andExpect(jsonPath("$.code").value("cannot_follow_self"))
                                .andExpect(jsonPath("$.message").value("Cannot follow yourself"))
                                .andExpect(jsonPath("$.requestId").value("follow-err-1"));
        }

        @Test
        void follow_duplicate_returnsConflict() throws Exception {
                // First follow succeeds
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Second follow should fail
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString())
                                .header("X-Request-Id", "follow-err-2"))
                                .andExpect(status().isConflict())
                                .andExpect(header().string("X-Request-Id", "follow-err-2"))
                                .andExpect(jsonPath("$.code").value("already_following"))
                                .andExpect(jsonPath("$.message").value("Already following"))
                                .andExpect(jsonPath("$.requestId").value("follow-err-2"));
        }

        @Test
        void follow_missingFollower_returnsNotFound() throws Exception {
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", UUID.randomUUID().toString())
                                .header("X-Request-Id", "follow-err-4"))
                                .andExpect(status().isNotFound())
                                .andExpect(header().string("X-Request-Id", "follow-err-4"))
                                .andExpect(jsonPath("$.code").value("follower_not_found"))
                                .andExpect(jsonPath("$.message").value("Follower not found"))
                                .andExpect(jsonPath("$.requestId").value("follow-err-4"));
        }

        @Test
        void follow_missingTargetUser_returnsNotFound() throws Exception {
                mockMvc.perform(post("/api/v1/users/{userId}/follow", UUID.randomUUID())
                                .header("X-User-Id", userAlice.getId().toString())
                                .header("X-Request-Id", "follow-err-5"))
                                .andExpect(status().isNotFound())
                                .andExpect(header().string("X-Request-Id", "follow-err-5"))
                                .andExpect(jsonPath("$.code").value("user_not_found"))
                                .andExpect(jsonPath("$.message").value("User to follow not found"))
                                .andExpect(jsonPath("$.requestId").value("follow-err-5"));
        }

        // ===== UNFOLLOW =====

        @Test
        void unfollow_removesRelationshipAndDecrementsCounters() throws Exception {
                // Follow first
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Unfollow
                mockMvc.perform(delete("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Verify Bob's follower count is back to 0
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userBob.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.followerCount").value(0));

                // Verify Alice's following count is back to 0
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userAlice.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.followingCount").value(0));
        }

        @Test
        void unfollow_whenNotFollowing_returnsNotFound() throws Exception {
                mockMvc.perform(delete("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString())
                                .header("X-Request-Id", "follow-err-3"))
                                .andExpect(status().isNotFound())
                                .andExpect(header().string("X-Request-Id", "follow-err-3"))
                                .andExpect(jsonPath("$.code").value("follow_not_found"))
                                .andExpect(jsonPath("$.message").value("Not following this user"))
                                .andExpect(jsonPath("$.requestId").value("follow-err-3"));
        }

        // ===== FOLLOWERS / FOLLOWING LISTS =====

        @Test
        void getFollowers_returnsFollowerList() throws Exception {
                // Alice follows Bob
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Get Bob's followers — should include Alice
                mockMvc.perform(get("/api/v1/users/{userId}/followers", userBob.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].username").value(userAlice.getUsername()));
        }

        @Test
        void getFollowers_invalidPage_returnsExplicitBadRequestContract() throws Exception {
                mockMvc.perform(get("/api/v1/users/{userId}/followers", userBob.getId())
                                .header("X-Request-Id", "follow-page-err-1")
                                .param("page", "later"))
                                .andExpect(status().isBadRequest())
                                .andExpect(header().string("X-Request-Id", "follow-page-err-1"))
                                .andExpect(jsonPath("$.code").value("invalid_user_follow_page"))
                                .andExpect(jsonPath("$.message").value("Invalid user follow page"))
                                .andExpect(jsonPath("$.requestId").value("follow-page-err-1"));
        }

        @Test
        void getFollowing_returnsFollowingList() throws Exception {
                // Alice follows Bob
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // Get Alice's following list — should include Bob
                mockMvc.perform(get("/api/v1/users/{userId}/following", userAlice.getId()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].username").value(userBob.getUsername()));
        }

        @Test
        void getFollowing_invalidSize_returnsExplicitBadRequestContract() throws Exception {
                mockMvc.perform(get("/api/v1/users/{userId}/following", userAlice.getId())
                                .header("X-Request-Id", "follow-page-err-2")
                                .param("size", "0"))
                                .andExpect(status().isBadRequest())
                                .andExpect(header().string("X-Request-Id", "follow-page-err-2"))
                                .andExpect(jsonPath("$.code").value("invalid_user_follow_size"))
                                .andExpect(jsonPath("$.message").value("Invalid user follow size"))
                                .andExpect(jsonPath("$.requestId").value("follow-page-err-2"));
        }

        // ===== FULL LIFECYCLE =====

        @Test
        void fullSocialLifecycle_followCheckUnfollow() throws Exception {
                // 1. Follow
                mockMvc.perform(post("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // 2. Verify following status
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(jsonPath("$.following").value(true))
                                .andExpect(jsonPath("$.followerCount").value(1));

                // 3. Unfollow
                mockMvc.perform(delete("/api/v1/users/{userId}/follow", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(status().isOk());

                // 4. Verify no longer following
                mockMvc.perform(get("/api/v1/users/{userId}/profile", userBob.getId())
                                .header("X-User-Id", userAlice.getId().toString()))
                                .andExpect(jsonPath("$.following").value(false))
                                .andExpect(jsonPath("$.followerCount").value(0));
        }
}
