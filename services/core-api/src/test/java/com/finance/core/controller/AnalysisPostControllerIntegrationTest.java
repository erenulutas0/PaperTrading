package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.AppUser;
import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.ActivityEventRepository;
import com.finance.core.repository.InteractionRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisPostControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private AnalysisPostRepository postRepository;
        @Autowired
        private ActivityEventRepository eventRepository;
        @Autowired
        private InteractionRepository interactionRepository;
        @Autowired
        private com.finance.core.repository.NotificationRepository notificationRepository;
        @org.springframework.test.context.bean.override.mockito.MockitoBean
        private BinanceService binanceService;

        private AppUser author;

        @BeforeEach
        void setUp() {
                when(binanceService.getPrices()).thenReturn(Map.of(
                                "BTCUSDT", 50000.0,
                                "ETHUSDT", 3000.0));

                notificationRepository.deleteAll();
                interactionRepository.deleteAll();
                eventRepository.deleteAll();
                postRepository.deleteAll();
                userRepository.deleteAll();

                // Reuse or create the test user
                author = userRepository.save(AppUser.builder()
                                .username("analyst_" + UUID.randomUUID().toString().substring(0, 5))
                                .email("analyst_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com")
                                .password("password123")
                                .displayName("The Analyst")
                                .build());
        }

        // ==================== CREATE POST ====================

        @Nested
        class CreatePostEndpoint {

                @Test
                void createPost_bullish_returnsPost() throws Exception {
                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("BTC to 60k")
                                        .content("Based on the breakout pattern, BTC is heading to 60k within a week.")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .targetPrice(BigDecimal.valueOf(60000))
                                        .targetDays(7)
                                        .timeframe("1W")
                                        .build();

                        mockMvc.perform(post("/api/v1/analysis-posts")
                                        .header("X-User-Id", author.getId().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.title").value("BTC to 60k"))
                                        .andExpect(jsonPath("$.instrumentSymbol").value("BTCUSDT"))
                                        .andExpect(jsonPath("$.direction").value("BULLISH"))
                                        .andExpect(jsonPath("$.targetPrice").value(60000))
                                        .andExpect(jsonPath("$.priceAtCreation").value(50000.0))
                                        .andExpect(jsonPath("$.outcome").value("PENDING"))
                                        .andExpect(jsonPath("$.authorUsername").value(author.getUsername()))
                                        .andExpect(jsonPath("$.targetDate").isNotEmpty())
                                        .andExpect(jsonPath("$.id").isNotEmpty());
                }

                @Test
                void createPost_bearish_returnsPost() throws Exception {
                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("ETH crash")
                                        .content("ETH is overvalued, expect drop to 2000.")
                                        .instrumentSymbol("ETHUSDT")
                                        .direction("BEARISH")
                                        .targetPrice(BigDecimal.valueOf(2000))
                                        .build();

                        mockMvc.perform(post("/api/v1/analysis-posts")
                                        .header("X-User-Id", author.getId().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.direction").value("BEARISH"))
                                        .andExpect(jsonPath("$.priceAtCreation").value(3000.0));
                }

                @Test
                void createPost_invalidDirection_returns400() throws Exception {
                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("SIDEWAYS")
                                        .build();

                        mockMvc.perform(post("/api/v1/analysis-posts")
                                        .header("X-User-Id", author.getId().toString())
                                        .header("X-Request-Id", "analysis-create-err-1")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(header().string("X-Request-Id", "analysis-create-err-1"))
                                        .andExpect(jsonPath("$.code").value("invalid_analysis_direction"))
                                        .andExpect(jsonPath("$.message").value("Invalid analysis direction"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-create-err-1"));
                }

                @Test
                void createPost_noMarketData_returns400() throws Exception {
                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("XYZUSDT")
                                        .direction("BULLISH")
                                        .build();

                        mockMvc.perform(post("/api/v1/analysis-posts")
                                        .header("X-User-Id", author.getId().toString())
                                        .header("X-Request-Id", "analysis-create-err-2")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isConflict())
                                        .andExpect(header().string("X-Request-Id", "analysis-create-err-2"))
                                        .andExpect(jsonPath("$.code").value("analysis_market_data_unavailable"))
                                        .andExpect(jsonPath("$.message").value(containsString("No market data")))
                                        .andExpect(jsonPath("$.requestId").value("analysis-create-err-2"));
                }

                @Test
                void createPost_bullishTargetBelowCurrent_returns400() throws Exception {
                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .targetPrice(BigDecimal.valueOf(40000)) // below 50k current
                                        .build();

                        mockMvc.perform(post("/api/v1/analysis-posts")
                                        .header("X-User-Id", author.getId().toString())
                                        .header("X-Request-Id", "analysis-create-err-3")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(header().string("X-Request-Id", "analysis-create-err-3"))
                                        .andExpect(jsonPath("$.code").value("invalid_analysis_target_price"))
                                        .andExpect(jsonPath("$.message")
                                                        .value(containsString("BULLISH target price must be above")))
                                        .andExpect(jsonPath("$.requestId").value("analysis-create-err-3"));
                }

                @Test
                void createPost_unknownUser_returnsExplicitNotFoundContract() throws Exception {
                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .build();

                        mockMvc.perform(post("/api/v1/analysis-posts")
                                        .header("X-User-Id", UUID.randomUUID().toString())
                                        .header("X-Request-Id", "analysis-create-err-4")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                                        .andExpect(status().isNotFound())
                                        .andExpect(header().string("X-Request-Id", "analysis-create-err-4"))
                                        .andExpect(jsonPath("$.code").value("user_not_found"))
                                        .andExpect(jsonPath("$.message").value("User not found"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-create-err-4"));
                }
        }

        // ==================== GET & FEED ====================

        @Nested
        class GetAndFeed {

                @Test
                void getPost_returnsPostById() throws Exception {
                        // Create a post first
                        String createResponse = createSamplePost();
                        String postId = objectMapper.readTree(createResponse).get("id").asText();

                        mockMvc.perform(get("/api/v1/analysis-posts/{postId}", postId))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id").value(postId))
                                        .andExpect(jsonPath("$.title").value("BTC Analysis"))
                                        .andExpect(jsonPath("$.outcome").value("PENDING"));
                }

                @Test
                void getPost_nonExistent_returns404() throws Exception {
                        mockMvc.perform(get("/api/v1/analysis-posts/{postId}", UUID.randomUUID())
                                        .header("X-Request-Id", "analysis-read-err-1"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(header().string("X-Request-Id", "analysis-read-err-1"))
                                        .andExpect(jsonPath("$.code").value("analysis_post_not_found"))
                                        .andExpect(jsonPath("$.message").value("Analysis post not found"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-read-err-1"));
                }

                @Test
                void getPost_orphanAuthor_returnsExplicitNotFoundContract() throws Exception {
                        AnalysisPost orphanPost = postRepository.save(AnalysisPost.builder()
                                        .authorId(UUID.randomUUID())
                                        .title("Orphan Analysis")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction(AnalysisPost.Direction.BULLISH)
                                        .priceAtCreation(BigDecimal.valueOf(50000))
                                        .outcome(AnalysisPost.Outcome.PENDING)
                                        .deleted(false)
                                        .build());

                        mockMvc.perform(get("/api/v1/analysis-posts/{postId}", orphanPost.getId())
                                        .header("X-Request-Id", "analysis-read-err-2"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(header().string("X-Request-Id", "analysis-read-err-2"))
                                        .andExpect(jsonPath("$.code").value("analysis_post_author_not_found"))
                                        .andExpect(jsonPath("$.message").value("Analysis post author not found"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-read-err-2"));
                }

                @Test
                void getFeed_returnsAllPosts() throws Exception {
                        createSamplePost();
                        createSamplePost();

                        mockMvc.perform(get("/api/v1/analysis-posts/feed"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));
                }

                @Test
                void getPostsByUser_returnsOnlyUserPosts() throws Exception {
                        createSamplePost();

                        mockMvc.perform(get("/api/v1/analysis-posts/user/{userId}", author.getId()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                                        .andExpect(jsonPath("$.content[0].authorId").value(author.getId().toString()));
                }

                @Test
                void getPostsByUser_unknownUser_returnsExplicitNotFoundContract() throws Exception {
                        mockMvc.perform(get("/api/v1/analysis-posts/user/{userId}", UUID.randomUUID())
                                        .header("X-Request-Id", "analysis-user-err-1"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(header().string("X-Request-Id", "analysis-user-err-1"))
                                        .andExpect(jsonPath("$.code").value("user_not_found"))
                                        .andExpect(jsonPath("$.message").value("User not found"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-user-err-1"));
                }
        }

        // ==================== DELETE ====================

        @Nested
        class DeleteEndpoint {

                @Test
                void deletePost_success() throws Exception {
                        String createResponse = createSamplePost();
                        String postId = objectMapper.readTree(createResponse).get("id").asText();

                        // Delete the post
                        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                        .header("X-User-Id", author.getId().toString()))
                                        .andExpect(status().isOk());

                        // Verify it's soft deleted in DB
                        AnalysisPost deleted = postRepository.findById(UUID.fromString(postId)).orElseThrow();
                        assertTrue(deleted.isDeleted());
                        assertNotNull(deleted.getDeletedAt());
                }

                @Test
                void deletePost_notAuthor_returns403() throws Exception {
                        String createResponse = createSamplePost();
                        String postId = objectMapper.readTree(createResponse).get("id").asText();

                        // Try to delete as a different user
                        AppUser otherUser = userRepository.save(AppUser.builder()
                                        .username("other_" + UUID.randomUUID().toString().substring(0, 5))
                                        .email("other_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com")
                                        .password("pass")
                                        .build());

                        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                        .header("X-User-Id", otherUser.getId().toString())
                                        .header("X-Request-Id", "analysis-delete-err-1"))
                                        .andExpect(status().isForbidden())
                                        .andExpect(header().string("X-Request-Id", "analysis-delete-err-1"))
                                        .andExpect(jsonPath("$.code").value("analysis_post_delete_forbidden"))
                                        .andExpect(jsonPath("$.message").value("Only the author can delete their post"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-delete-err-1"));
                }

                @Test
                void deletePost_alreadyDeleted_returns409() throws Exception {
                        String createResponse = createSamplePost();
                        String postId = objectMapper.readTree(createResponse).get("id").asText();

                        // Delete once
                        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                        .header("X-User-Id", author.getId().toString()))
                                        .andExpect(status().isOk());

                        // Delete again
                        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                        .header("X-User-Id", author.getId().toString())
                                        .header("X-Request-Id", "analysis-delete-err-2"))
                                        .andExpect(status().isConflict())
                                        .andExpect(header().string("X-Request-Id", "analysis-delete-err-2"))
                                        .andExpect(jsonPath("$.code").value("analysis_post_already_deleted"))
                                        .andExpect(jsonPath("$.message").value("Analysis post already deleted"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-delete-err-2"));
                }

                @Test
                void deletePost_unknownUser_returnsExplicitNotFoundContract() throws Exception {
                        String createResponse = createSamplePost();
                        String postId = objectMapper.readTree(createResponse).get("id").asText();

                        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                        .header("X-User-Id", UUID.randomUUID().toString())
                                        .header("X-Request-Id", "analysis-delete-err-3"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(header().string("X-Request-Id", "analysis-delete-err-3"))
                                        .andExpect(jsonPath("$.code").value("user_not_found"))
                                        .andExpect(jsonPath("$.message").value("User not found"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-delete-err-3"));
                }

                @Test
                void deletedPost_disappearsFromFeed() throws Exception {
                        String createResponse = createSamplePost();
                        String postId = objectMapper.readTree(createResponse).get("id").asText();

                        // Delete
                        mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                        .header("X-User-Id", author.getId().toString()))
                                        .andExpect(status().isOk());

                        // Feed should not contain this post
                        mockMvc.perform(get("/api/v1/analysis-posts/feed"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").doesNotExist());
                }
        }

        // ==================== AUTHOR STATS ====================

        @Nested
        class StatsEndpoint {

                @Test
                void getAuthorStats_returnsStats() throws Exception {
                        createSamplePost();

                        mockMvc.perform(get("/api/v1/analysis-posts/user/{userId}/stats", author.getId()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)))
                                        .andExpect(jsonPath("$.hits").value(0))
                                        .andExpect(jsonPath("$.pending").value(greaterThanOrEqualTo(1)));
                }

                @Test
                void getAuthorStats_unknownUser_returnsExplicitNotFoundContract() throws Exception {
                        mockMvc.perform(get("/api/v1/analysis-posts/user/{userId}/stats", UUID.randomUUID())
                                        .header("X-Request-Id", "analysis-stats-err-1"))
                                        .andExpect(status().isNotFound())
                                        .andExpect(header().string("X-Request-Id", "analysis-stats-err-1"))
                                        .andExpect(jsonPath("$.code").value("user_not_found"))
                                        .andExpect(jsonPath("$.message").value("User not found"))
                                        .andExpect(jsonPath("$.requestId").value("analysis-stats-err-1"));
                }
        }

        // ==================== FULL LIFECYCLE ====================

        @Test
        void fullLifecycle_createPostThenVerifyThenDelete() throws Exception {
                // 1. Create a post
                AnalysisPostRequest request = AnalysisPostRequest.builder()
                                .title("Full Lifecycle Test")
                                .content("This post will go through the full lifecycle")
                                .instrumentSymbol("BTCUSDT")
                                .direction("BULLISH")
                                .targetPrice(BigDecimal.valueOf(55000))
                                .stopPrice(BigDecimal.valueOf(48000))
                                .timeframe("1W")
                                .targetDays(7)
                                .build();

                String createResponse = mockMvc.perform(post("/api/v1/analysis-posts")
                                .header("X-User-Id", author.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String postId = objectMapper.readTree(createResponse).get("id").asText();

                // 2. Verify it appears in feed
                mockMvc.perform(get("/api/v1/analysis-posts/feed"))
                                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").exists());

                // 3. Verify it appears in user's posts
                mockMvc.perform(get("/api/v1/analysis-posts/user/{userId}", author.getId()))
                                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").exists());

                // 4. Delete it
                mockMvc.perform(delete("/api/v1/analysis-posts/{postId}", postId)
                                .header("X-User-Id", author.getId().toString()))
                                .andExpect(status().isOk());

                // 5. Verify it's gone from feed
                // 5. Verify it's gone from feed
                mockMvc.perform(get("/api/v1/analysis-posts/feed"))
                                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").doesNotExist());

                // 6. But still accessible by direct ID (tombstone)
                mockMvc.perform(get("/api/v1/analysis-posts/{postId}", postId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.deleted").value(true))
                                .andExpect(jsonPath("$.title").value("[Deleted]"))
                                .andExpect(jsonPath("$.instrumentSymbol").value("BTCUSDT"))
                                .andExpect(jsonPath("$.priceAtCreation").value(50000.0));

                // 7. Check stats
                mockMvc.perform(get("/api/v1/analysis-posts/user/{userId}/stats", author.getId()))
                                .andExpect(status().isOk());
        }

        // ==================== HELPERS ====================

        private String createSamplePost() throws Exception {
                AnalysisPostRequest request = AnalysisPostRequest.builder()
                                .title("BTC Analysis")
                                .content("BTC looking strong")
                                .instrumentSymbol("BTCUSDT")
                                .direction("BULLISH")
                                .targetPrice(BigDecimal.valueOf(55000))
                                .build();

                return mockMvc.perform(post("/api/v1/analysis-posts")
                                .header("X-User-Id", author.getId().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
        }
}
