package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.AppUser;
import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.dto.AnalysisPostResponse;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisPostServiceTest {

        @Mock
        private AnalysisPostRepository postRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private BinanceService binanceService;
        @Mock
        private ActivityFeedService activityFeedService;

        @InjectMocks
        private AnalysisPostService postService;

        private UUID authorId;
        private AppUser author;

        @BeforeEach
        void setUp() {
                authorId = UUID.randomUUID();
                author = AppUser.builder()
                                .id(authorId)
                                .username("trader1")
                                .displayName("Top Trader")
                                .email("trader@test.com")
                                .password("pass")
                                .build();
                org.springframework.test.util.ReflectionTestUtils.setField(postService, "self", postService);
        }

        // ==================== CREATE POST ====================

        @Nested
        class CreatePost {

                @Test
                void createPost_bullish_success() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
                        when(postRepository.save(any())).thenAnswer(inv -> {
                                AnalysisPost p = inv.getArgument(0);
                                p.setId(UUID.randomUUID());
                                return p;
                        });
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(1L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("BTC to the moon")
                                        .content("I think BTC will hit 60k")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .targetPrice(BigDecimal.valueOf(60000))
                                        .targetDays(7)
                                        .build();

                        AnalysisPostResponse response = postService.createPost(authorId, request);

                        assertNotNull(response.getId());
                        assertEquals("BTC to the moon", response.getTitle());
                        assertEquals("BTCUSDT", response.getInstrumentSymbol());
                        assertEquals("BULLISH", response.getDirection());
                        assertEquals(BigDecimal.valueOf(60000), response.getTargetPrice());
                        assertEquals(BigDecimal.valueOf(50000.0), response.getPriceAtCreation());
                        assertEquals("PENDING", response.getOutcome());
                        assertEquals("trader1", response.getAuthorUsername());
                        assertEquals("Top Trader", response.getAuthorDisplayName());
                        assertNotNull(response.getTargetDate());
                }

                @Test
                void createPost_bearish_success() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("ETHUSDT", 3000.0));
                        when(postRepository.save(any())).thenAnswer(inv -> {
                                AnalysisPost p = inv.getArgument(0);
                                p.setId(UUID.randomUUID());
                                return p;
                        });
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(1L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("ETH crash incoming")
                                        .content("ETH is overvalued")
                                        .instrumentSymbol("ETHUSDT")
                                        .direction("BEARISH")
                                        .targetPrice(BigDecimal.valueOf(2000))
                                        .timeframe("1M")
                                        .build();

                        AnalysisPostResponse response = postService.createPost(authorId, request);

                        assertEquals("BEARISH", response.getDirection());
                        assertEquals(BigDecimal.valueOf(2000), response.getTargetPrice());
                        assertEquals(BigDecimal.valueOf(3000.0), response.getPriceAtCreation());
                }

                @Test
                void createPost_neutral_noTarget_success() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("SOLUSDT", 100.0));
                        when(postRepository.save(any())).thenAnswer(inv -> {
                                AnalysisPost p = inv.getArgument(0);
                                p.setId(UUID.randomUUID());
                                return p;
                        });
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(1L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("SOL analysis")
                                        .content("SOL looks neutral")
                                        .instrumentSymbol("SOLUSDT")
                                        .direction("NEUTRAL")
                                        .build();

                        AnalysisPostResponse response = postService.createPost(authorId, request);
                        assertNull(response.getTargetPrice());
                        assertNull(response.getTargetDate());
                }

                @Test
                void createPost_symbolUppercased() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
                        when(postRepository.save(any())).thenAnswer(inv -> {
                                AnalysisPost p = inv.getArgument(0);
                                p.setId(UUID.randomUUID());
                                return p;
                        });
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(1L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("btcusdt") // lowercase
                                        .direction("BULLISH")
                                        .targetPrice(BigDecimal.valueOf(60000))
                                        .build();

                        AnalysisPostResponse response = postService.createPost(authorId, request);
                        assertEquals("BTCUSDT", response.getInstrumentSymbol());
                }

                @Test
                void createPost_invalidDirection_throws() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("SIDEWAYS")
                                        .build();

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> postService.createPost(authorId, request));
                        assertTrue(ex.getMessage().contains("Invalid direction"));
                }

                @Test
                void createPost_noMarketData_throws() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of()); // empty

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("XYZUSDT")
                                        .direction("BULLISH")
                                        .build();

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> postService.createPost(authorId, request));
                        assertTrue(ex.getMessage().contains("No market data"));
                }

                @Test
                void createPost_bullishTargetBelowCurrent_throws() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .targetPrice(BigDecimal.valueOf(40000)) // below current
                                        .build();

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> postService.createPost(authorId, request));
                        assertTrue(ex.getMessage().contains("BULLISH target price must be above"));
                }

                @Test
                void createPost_bearishTargetAboveCurrent_throws() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BEARISH")
                                        .targetPrice(BigDecimal.valueOf(60000)) // above current
                                        .build();

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> postService.createPost(authorId, request));
                        assertTrue(ex.getMessage().contains("BEARISH target price must be below"));
                }

                @Test
                void createPost_userNotFound_throws() {
                        when(userRepository.findById(any())).thenReturn(Optional.empty());

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .build();

                        assertThrows(RuntimeException.class,
                                        () -> postService.createPost(UUID.randomUUID(), request));
                }

                @Test
                void createPost_snapshotsPrice() {
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 52345.67));
                        when(postRepository.save(any())).thenAnswer(inv -> {
                                AnalysisPost p = inv.getArgument(0);
                                p.setId(UUID.randomUUID());
                                return p;
                        });
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(1L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        AnalysisPostRequest request = AnalysisPostRequest.builder()
                                        .title("Test")
                                        .content("Content")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction("BULLISH")
                                        .targetPrice(BigDecimal.valueOf(60000))
                                        .build();

                        AnalysisPostResponse response = postService.createPost(authorId, request);

                        // Verify the saved entity has the correct snapshotted price
                        ArgumentCaptor<AnalysisPost> captor = ArgumentCaptor.forClass(AnalysisPost.class);
                        verify(postRepository).save(captor.capture());
                        assertEquals(BigDecimal.valueOf(52345.67), captor.getValue().getPriceAtCreation());
                }
        }

        // ==================== DELETE POST ====================

        @Nested
        class DeletePost {

                @Test
                void deletePost_success() {
                        UUID postId = UUID.randomUUID();
                        AnalysisPost post = AnalysisPost.builder()
                                        .id(postId)
                                        .authorId(authorId)
                                        .deleted(false)
                                        .build();
                        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

                        postService.deletePost(postId, authorId);

                        assertTrue(post.isDeleted());
                        assertNotNull(post.getDeletedAt());
                        verify(postRepository).save(post);
                }

                @Test
                void deletePost_notAuthor_throws() {
                        UUID postId = UUID.randomUUID();
                        UUID otherId = UUID.randomUUID();
                        AnalysisPost post = AnalysisPost.builder()
                                        .id(postId)
                                        .authorId(authorId)
                                        .deleted(false)
                                        .build();
                        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> postService.deletePost(postId, otherId));
                        assertTrue(ex.getMessage().contains("Only the author"));
                }

                @Test
                void deletePost_alreadyDeleted_throws() {
                        UUID postId = UUID.randomUUID();
                        AnalysisPost post = AnalysisPost.builder()
                                        .id(postId)
                                        .authorId(authorId)
                                        .deleted(true)
                                        .build();
                        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> postService.deletePost(postId, authorId));
                        assertTrue(ex.getMessage().contains("already deleted"));
                }

                @Test
                void deletePost_notFound_throws() {
                        when(postRepository.findById(any())).thenReturn(Optional.empty());

                        assertThrows(RuntimeException.class,
                                        () -> postService.deletePost(UUID.randomUUID(), authorId));
                }
        }

        // ==================== GET POST ====================

        @Nested
        class GetPost {

                @Test
                void getPost_success() {
                        UUID postId = UUID.randomUUID();
                        AnalysisPost post = AnalysisPost.builder()
                                        .id(postId)
                                        .authorId(authorId)
                                        .title("BTC Analysis")
                                        .content("Content here")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction(AnalysisPost.Direction.BULLISH)
                                        .priceAtCreation(BigDecimal.valueOf(50000))
                                        .outcome(AnalysisPost.Outcome.PENDING)
                                        .deleted(false)
                                        .build();

                        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(5L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(3L);

                        AnalysisPostResponse response = postService.getPost(postId);

                        assertEquals("BTC Analysis", response.getTitle());
                        assertEquals(5, response.getAuthorTotalPosts());
                        assertEquals(3, response.getAuthorHitCount());
                }

                @Test
                void getPost_deletedPost_showsTombstone() {
                        UUID postId = UUID.randomUUID();
                        AnalysisPost post = AnalysisPost.builder()
                                        .id(postId)
                                        .authorId(authorId)
                                        .title("Secret Analysis")
                                        .content("Very important analysis")
                                        .instrumentSymbol("BTCUSDT")
                                        .direction(AnalysisPost.Direction.BULLISH)
                                        .priceAtCreation(BigDecimal.valueOf(50000))
                                        .outcome(AnalysisPost.Outcome.PENDING)
                                        .deleted(true)
                                        .build();

                        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(0L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        AnalysisPostResponse response = postService.getPost(postId);

                        assertEquals("[Deleted]", response.getTitle());
                        assertTrue(response.getContent().contains("removed by the author"));
                        assertTrue(response.isDeleted());
                        // But metadata is preserved!
                        assertEquals("BTCUSDT", response.getInstrumentSymbol());
                        assertEquals("BULLISH", response.getDirection());
                }
        }

        // ==================== FEED & AUTHOR POSTS ====================

        @Nested
        class FeedAndAuthorPosts {

                @Test
                void getFeed_returnsAllNonDeletedPosts() {
                        AnalysisPost post1 = createSamplePost("BTC Up", AnalysisPost.Direction.BULLISH);
                        AnalysisPost post2 = createSamplePost("ETH Down", AnalysisPost.Direction.BEARISH);
                        Pageable pageable = PageRequest.of(0, 20);

                        when(postRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable))
                                        .thenReturn(new PageImpl<>(List.of(post1, post2)));
                        when(userRepository.findById(any())).thenReturn(Optional.of(author));
                        when(postRepository.countByAuthorIdAndDeletedFalse(any())).thenReturn(2L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(any(), any())).thenReturn(0L);

                        Page<AnalysisPostResponse> feed = postService.getFeed(pageable);

                        assertEquals(2, feed.getTotalElements());
                }

                @Test
                void getPostsByAuthor_returnsOnlyAuthorPosts() {
                        AnalysisPost post = createSamplePost("My Analysis", AnalysisPost.Direction.BULLISH);
                        Pageable pageable = PageRequest.of(0, 20);

                        when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
                        when(postRepository.findByAuthorIdAndDeletedFalseOrderByCreatedAtDesc(authorId, pageable))
                                        .thenReturn(new PageImpl<>(List.of(post)));
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(1L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(0L);

                        Page<AnalysisPostResponse> posts = postService.getPostsByAuthor(authorId, pageable);

                        assertEquals(1, posts.getTotalElements());
                        assertEquals("My Analysis", posts.getContent().get(0).getTitle());
                }
        }

        // ==================== AUTHOR STATS ====================

        @Nested
        class AuthorStats {

                @Test
                void getAuthorStats_returnsCorrectCounts() {
                        when(postRepository.countByAuthorIdAndDeletedFalse(authorId)).thenReturn(10L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.HIT))
                                        .thenReturn(6L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.MISSED))
                                        .thenReturn(2L);
                        when(postRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                                        AnalysisPost.Outcome.PENDING))
                                        .thenReturn(2L);

                        Map<String, Long> stats = postService.getAuthorStats(authorId);

                        assertEquals(10L, stats.get("total"));
                        assertEquals(6L, stats.get("hits"));
                        assertEquals(2L, stats.get("misses"));
                        assertEquals(2L, stats.get("pending"));
                }
        }

        // ==================== HELPERS ====================

        private AnalysisPost createSamplePost(String title, AnalysisPost.Direction direction) {
                return AnalysisPost.builder()
                                .id(UUID.randomUUID())
                                .authorId(authorId)
                                .title(title)
                                .content("Analysis content")
                                .instrumentSymbol("BTCUSDT")
                                .direction(direction)
                                .priceAtCreation(BigDecimal.valueOf(50000))
                                .outcome(AnalysisPost.Outcome.PENDING)
                                .deleted(false)
                                .build();
        }
}
