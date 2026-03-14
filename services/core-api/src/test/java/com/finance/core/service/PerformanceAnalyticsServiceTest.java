package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.domain.TradeActivity;
import com.finance.core.dto.MarketInstrumentResponse;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
import com.finance.core.repository.TradeActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceAnalyticsServiceTest {

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private AnalysisPostRepository analysisPostRepository;

    @Mock
    private TradeActivityRepository tradeActivityRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private MarketDataFacadeService marketDataFacadeService;

    @InjectMocks
    private PerformanceAnalyticsService analyticsService;

    private UUID portfolioId;
    private UUID authorId;

    @BeforeEach
    void setUp() {
        portfolioId = UUID.randomUUID();
        authorId = UUID.randomUUID();
    }

    @Test
    void calculateMaxDrawdown_emptySnapshots_returnsZero() {
        when(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId))
                .thenReturn(Collections.emptyList());

        double result = analyticsService.calculateMaxDrawdown(portfolioId);

        assertEquals(0.0, result, 0.001);
    }

    @Test
    void calculateMaxDrawdown_calculatesCorrectly() {
        // Equity progression: 100, 120, 90, 150.
        // Peak = 120, Trough = 90. Drawdown = (120 - 90)/120 = 25%
        PortfolioSnapshot s1 = createSnapshot(BigDecimal.valueOf(100));
        PortfolioSnapshot s2 = createSnapshot(BigDecimal.valueOf(120));
        PortfolioSnapshot s3 = createSnapshot(BigDecimal.valueOf(90));
        PortfolioSnapshot s4 = createSnapshot(BigDecimal.valueOf(150));

        when(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId))
                .thenReturn(Arrays.asList(s1, s2, s3, s4));

        double result = analyticsService.calculateMaxDrawdown(portfolioId);

        assertEquals(25.0, result, 0.001); // 25.0%
    }

    @Test
    void calculateWinRate_calculatesCorrectly() {
        // Hits: 6, Missed: 3, Expired: 1 -> Total = 10, WR = 60%
        when(analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId, AnalysisPost.Outcome.HIT))
                .thenReturn(6L);
        when(analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId, AnalysisPost.Outcome.MISSED))
                .thenReturn(3L);
        when(analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId, AnalysisPost.Outcome.EXPIRED))
                .thenReturn(1L);

        double result = analyticsService.calculateWinRate(authorId);

        assertEquals(60.0, result, 0.001);
    }

    @Test
    void calculateWinRate_noResolvedPosts_returnsZero() {
        when(analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(any(), any()))
                .thenReturn(0L);

        double result = analyticsService.calculateWinRate(authorId);

        assertEquals(0.0, result, 0.001);
    }

    @Test
    void calculateSharpeRatio_lessThanTwoSnapshots_returnsZero() {
        when(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId))
                .thenReturn(Collections.singletonList(createSnapshot(BigDecimal.valueOf(100))));

        double result = analyticsService.calculateSharpeRatio(portfolioId);

        assertEquals(0.0, result, 0.001);
    }

    @Test
    void calculateSharpeRatio_calculatesCorrectly() {
        // Equities: 100, 120 (+0.2), 108 (-0.1)
        // Avg Return = 0.05
        // Variance = (0.0225 + 0.0225) / 2 = 0.0225
        // StdDev = 0.15
        // Sharpe = 0.05 / 0.15 = 0.333333...

        PortfolioSnapshot s1 = createSnapshot(BigDecimal.valueOf(100));
        PortfolioSnapshot s2 = createSnapshot(BigDecimal.valueOf(120));
        PortfolioSnapshot s3 = createSnapshot(BigDecimal.valueOf(108));

        when(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId))
                .thenReturn(Arrays.asList(s1, s2, s3));

        double result = analyticsService.calculateSharpeRatio(portfolioId);

        assertEquals(0.333333, result, 0.00001);
    }

    @Test
    void calculateSharpeRatio_zeroStandardDeviation() {
        // Equities: 100, 105, 110.25 (Constant 5% return)
        PortfolioSnapshot s1 = createSnapshot(BigDecimal.valueOf(100));
        PortfolioSnapshot s2 = createSnapshot(BigDecimal.valueOf(105));
        PortfolioSnapshot s3 = createSnapshot(BigDecimal.valueOf(110.25));

        when(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId))
                .thenReturn(Arrays.asList(s1, s2, s3));

        double result = analyticsService.calculateSharpeRatio(portfolioId);

        // Constant positive returns should give a high positive sharpe (our logic
        // returns 999.0)
        assertEquals(999.0, result, 0.001);
    }

    @Test
    void getFullAnalytics_shouldIncludeSummaryBlock() {
        Portfolio portfolio = Portfolio.builder()
                .id(portfolioId)
                .name("Alpha")
                .balance(BigDecimal.valueOf(100000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .ownerId(authorId.toString())
                .items(Arrays.asList(
                        PortfolioItem.builder()
                                .symbol("BTCUSDT")
                                .quantity(BigDecimal.valueOf(2))
                                .averagePrice(BigDecimal.valueOf(50000))
                                .side("LONG")
                                .leverage(1)
                                .build()))
                .build();
        PortfolioSnapshot s1 = createSnapshot(BigDecimal.valueOf(100000));
        PortfolioSnapshot s2 = createSnapshot(BigDecimal.valueOf(105000));

        when(portfolioRepository.findWithItemsById(portfolioId)).thenReturn(Optional.of(portfolio));
        when(snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId)).thenReturn(Arrays.asList(s1, s2));
        when(analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(any(), any())).thenReturn(0L);
        when(tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(portfolioId)).thenReturn(List.of(
                TradeActivity.builder()
                        .portfolioId(portfolioId)
                        .symbol("BTCUSDT")
                        .type("SELL")
                        .side("LONG")
                        .quantity(BigDecimal.ONE)
                        .price(BigDecimal.valueOf(51000))
                        .realizedPnl(BigDecimal.valueOf(1500))
                        .timestamp(LocalDateTime.now().minusMinutes(1))
                        .build()));
        when(marketDataFacadeService.getInstrumentSnapshots(any())).thenReturn(Map.of(
                "BTCUSDT",
                MarketInstrumentResponse.builder()
                        .symbol("BTCUSDT")
                        .currentPrice(52000.0)
                        .build()));

        Map<String, Object> result = analyticsService.getFullAnalytics(portfolioId, authorId);
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        Map<String, Object> positionSummary = (Map<String, Object>) result.get("positionSummary");
        Map<String, Object> performanceWindows = (Map<String, Object>) result.get("performanceWindows");
        Map<String, Object> periodExtremes = (Map<String, Object>) result.get("periodExtremes");
        java.util.List<Map<String, Object>> symbolAttribution = (java.util.List<Map<String, Object>>) result.get("symbolAttribution");
        java.util.List<Map<String, Object>> riskAttribution = (java.util.List<Map<String, Object>>) result.get("riskAttribution");
        java.util.List<Map<String, Object>> pnlTimeline = (java.util.List<Map<String, Object>>) result.get("pnlTimeline");

        assertEquals("Alpha", summary.get("portfolioName"));
        assertEquals("PUBLIC", summary.get("visibility"));
        assertEquals(100000.0, (double) summary.get("startingEquity"), 0.001);
        assertEquals(105000.0, (double) summary.get("currentEquity"), 0.001);
        assertEquals(5000.0, (double) summary.get("absoluteReturn"), 0.001);
        assertEquals(5.0, (double) summary.get("returnPercentage"), 0.001);
        assertEquals(1, positionSummary.get("openPositions"));
        assertEquals(104000.0, (double) positionSummary.get("grossExposure"), 0.001);
        assertEquals(1500.0, (double) positionSummary.get("realizedPnl"), 0.001);
        assertEquals(4000.0, (double) positionSummary.get("unrealizedPnl"), 0.001);
        assertEquals(2, performanceWindows.size());
        assertEquals(2, periodExtremes.size());
        assertEquals(1, symbolAttribution.size());
        assertEquals(1, riskAttribution.size());
        assertEquals("BTCUSDT", riskAttribution.get(0).get("symbol"));
        assertEquals(104000.0, (double) riskAttribution.get(0).get("exposure"), 0.001);
        assertEquals(100.0, (double) riskAttribution.get(0).get("exposureShare"), 0.001);
        assertEquals(2, pnlTimeline.size());
    }

    private PortfolioSnapshot createSnapshot(BigDecimal equity) {
        return PortfolioSnapshot.builder()
                .portfolioId(portfolioId)
                .totalEquity(equity)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
