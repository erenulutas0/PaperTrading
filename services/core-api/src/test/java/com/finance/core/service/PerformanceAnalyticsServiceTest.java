package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceAnalyticsServiceTest {

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private AnalysisPostRepository analysisPostRepository;

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

    private PortfolioSnapshot createSnapshot(BigDecimal equity) {
        return PortfolioSnapshot.builder()
                .portfolioId(portfolioId)
                .totalEquity(equity)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
