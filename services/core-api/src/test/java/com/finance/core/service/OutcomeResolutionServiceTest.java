package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.repository.AnalysisPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutcomeResolutionServiceTest {

    @Mock
    private AnalysisPostRepository postRepository;
    @Mock
    private BinanceService binanceService;
    @Mock
    private org.springframework.cache.CacheManager cacheManager;

    @InjectMocks
    private OutcomeResolutionService resolutionService;

    // ==================== TARGET HIT ====================

    @Nested
    class TargetHit {

        @Test
        void bullish_targetHit_whenPriceAboveTarget() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), null);
            // Current price is at or above target
            resolutionService.resolvePost(post, 60000.0);

            assertEquals(AnalysisPost.Outcome.HIT, post.getOutcome());
            assertNotNull(post.getOutcomeResolvedAt());
            assertEquals(BigDecimal.valueOf(60000.0), post.getPriceAtResolution());
            verify(postRepository).save(post);
        }

        @Test
        void bullish_targetNotHit_whenPriceBelowTarget() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), null);
            resolutionService.resolvePost(post, 55000.0);

            assertEquals(AnalysisPost.Outcome.PENDING, post.getOutcome());
            verify(postRepository, never()).save(any());
        }

        @Test
        void bearish_targetHit_whenPriceBelowTarget() {
            AnalysisPost post = createBearishPost(BigDecimal.valueOf(40000), null);
            resolutionService.resolvePost(post, 40000.0);

            assertEquals(AnalysisPost.Outcome.HIT, post.getOutcome());
            verify(postRepository).save(post);
        }

        @Test
        void bearish_targetNotHit_whenPriceAboveTarget() {
            AnalysisPost post = createBearishPost(BigDecimal.valueOf(40000), null);
            resolutionService.resolvePost(post, 45000.0);

            assertEquals(AnalysisPost.Outcome.PENDING, post.getOutcome());
            verify(postRepository, never()).save(any());
        }

        @Test
        void bullish_targetHit_overshot() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), null);
            // Price went way past the target
            resolutionService.resolvePost(post, 75000.0);

            assertEquals(AnalysisPost.Outcome.HIT, post.getOutcome());
        }
    }

    // ==================== STOP HIT ====================

    @Nested
    class StopHit {

        @Test
        void bullish_stopHit_whenPriceFallsBelowStop() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), BigDecimal.valueOf(45000));
            resolutionService.resolvePost(post, 44000.0);

            assertEquals(AnalysisPost.Outcome.MISSED, post.getOutcome());
            assertNotNull(post.getOutcomeResolvedAt());
            verify(postRepository).save(post);
        }

        @Test
        void bearish_stopHit_whenPriceRisesAboveStop() {
            AnalysisPost post = createBearishPost(BigDecimal.valueOf(40000), BigDecimal.valueOf(55000));
            resolutionService.resolvePost(post, 56000.0);

            assertEquals(AnalysisPost.Outcome.MISSED, post.getOutcome());
            verify(postRepository).save(post);
        }

        @Test
        void bullish_stopNotHit_whenPriceAboveStop() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), BigDecimal.valueOf(45000));
            resolutionService.resolvePost(post, 48000.0); // above stop, below target

            assertEquals(AnalysisPost.Outcome.PENDING, post.getOutcome());
            verify(postRepository, never()).save(any());
        }

        @Test
        void targetHitTakesPriority_overStopCheck() {
            // When both target and stop could theoretically be hit (edge case),
            // target check happens first
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), BigDecimal.valueOf(45000));
            resolutionService.resolvePost(post, 61000.0); // target hit

            assertEquals(AnalysisPost.Outcome.HIT, post.getOutcome());
            // save should be called only once (for target hit, not stop check)
            verify(postRepository, times(1)).save(post);
        }
    }

    // ==================== EXPIRY ====================

    @Nested
    class Expiry {

        @Test
        void expiredPosts_markedAsExpired() {
            AnalysisPost expiredPost = createBullishPost(BigDecimal.valueOf(60000), null);
            expiredPost.setTargetDate(LocalDateTime.now().minusDays(1)); // past deadline

            when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 55000.0));
            when(postRepository.findByOutcomeAndInstrumentSymbolAndDeletedFalse(
                    AnalysisPost.Outcome.PENDING, "BTCUSDT")).thenReturn(List.of());
            when(postRepository.findByOutcomeAndTargetDateBeforeAndDeletedFalse(
                    eq(AnalysisPost.Outcome.PENDING), any())).thenReturn(List.of(expiredPost));

            resolutionService.resolveOutcomes();

            assertEquals(AnalysisPost.Outcome.EXPIRED, expiredPost.getOutcome());
            assertNotNull(expiredPost.getOutcomeResolvedAt());
            assertEquals(BigDecimal.valueOf(55000.0), expiredPost.getPriceAtResolution());
        }

        @Test
        void expiredPost_noMarketData_notResolved() {
            AnalysisPost expiredPost = AnalysisPost.builder()
                    .id(UUID.randomUUID())
                    .instrumentSymbol("XYZUSDT") // no price data
                    .direction(AnalysisPost.Direction.BULLISH)
                    .targetPrice(BigDecimal.valueOf(100))
                    .outcome(AnalysisPost.Outcome.PENDING)
                    .targetDate(LocalDateTime.now().minusDays(1))
                    .build();

            when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
            when(postRepository.findByOutcomeAndInstrumentSymbolAndDeletedFalse(any(), any()))
                    .thenReturn(List.of());
            when(postRepository.findByOutcomeAndTargetDateBeforeAndDeletedFalse(any(), any()))
                    .thenReturn(List.of(expiredPost));

            resolutionService.resolveOutcomes();

            // Should stay PENDING because we have no price data for XYZUSDT
            assertEquals(AnalysisPost.Outcome.PENDING, expiredPost.getOutcome());
        }
    }

    // ==================== EDGE CASES ====================

    @Nested
    class EdgeCases {

        @Test
        void noPost_withNoTarget_remainsPending() {
            AnalysisPost post = AnalysisPost.builder()
                    .id(UUID.randomUUID())
                    .instrumentSymbol("BTCUSDT")
                    .direction(AnalysisPost.Direction.NEUTRAL)
                    .outcome(AnalysisPost.Outcome.PENDING)
                    .priceAtCreation(BigDecimal.valueOf(50000))
                    .build();

            resolutionService.resolvePost(post, 55000.0);

            assertEquals(AnalysisPost.Outcome.PENDING, post.getOutcome());
            verify(postRepository, never()).save(any());
        }

        @Test
        void emptyPrices_skipsResolution() {
            when(binanceService.getPrices()).thenReturn(Map.of());

            resolutionService.resolveOutcomes();

            verify(postRepository, never()).findByOutcomeAndInstrumentSymbolAndDeletedFalse(any(), any());
        }

        @Test
        void exactTargetPrice_isHit() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), null);
            resolutionService.resolvePost(post, 60000.0); // exactly at target

            assertEquals(AnalysisPost.Outcome.HIT, post.getOutcome());
        }

        @Test
        void exactStopPrice_isMissed() {
            AnalysisPost post = createBullishPost(BigDecimal.valueOf(60000), BigDecimal.valueOf(45000));
            resolutionService.resolvePost(post, 45000.0); // exactly at stop

            assertEquals(AnalysisPost.Outcome.MISSED, post.getOutcome());
        }
    }

    // ==================== HELPERS ====================

    private AnalysisPost createBullishPost(BigDecimal targetPrice, BigDecimal stopPrice) {
        return AnalysisPost.builder()
                .id(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .instrumentSymbol("BTCUSDT")
                .direction(AnalysisPost.Direction.BULLISH)
                .targetPrice(targetPrice)
                .stopPrice(stopPrice)
                .priceAtCreation(BigDecimal.valueOf(50000))
                .outcome(AnalysisPost.Outcome.PENDING)
                .deleted(false)
                .build();
    }

    private AnalysisPost createBearishPost(BigDecimal targetPrice, BigDecimal stopPrice) {
        return AnalysisPost.builder()
                .id(UUID.randomUUID())
                .authorId(UUID.randomUUID())
                .instrumentSymbol("BTCUSDT")
                .direction(AnalysisPost.Direction.BEARISH)
                .targetPrice(targetPrice)
                .stopPrice(stopPrice)
                .priceAtCreation(BigDecimal.valueOf(50000))
                .outcome(AnalysisPost.Outcome.PENDING)
                .deleted(false)
                .build();
    }
}
