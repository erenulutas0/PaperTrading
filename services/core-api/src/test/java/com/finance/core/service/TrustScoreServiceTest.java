package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.TrustScoreSnapshot;
import com.finance.core.dto.TrustScoreBreakdownResponse;
import com.finance.core.dto.TrustScoreHistoryPointResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.TrustScoreSnapshotRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrustScoreServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PerformanceAnalyticsService analyticsService;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TradeActivityRepository tradeActivityRepository;

    @Mock
    private TrustScoreSnapshotRepository trustScoreSnapshotRepository;

    @Mock
    private PerformanceCalculationService performanceCalculationService;

    @Mock
    private BinanceService binanceService;

    @InjectMocks
    private TrustScoreService trustScoreService;

    private AppUser userA;
    private AppUser userB;
    private AppUser userC;
    private AppUser userD;

    @BeforeEach
    void setUp() {
        userA = new AppUser();
        userA.setId(UUID.randomUUID());
        userA.setTrustScore(50.0);

        userB = new AppUser();
        userB.setId(UUID.randomUUID());
        userB.setTrustScore(50.0);

        userC = new AppUser();
        userC.setId(UUID.randomUUID());
        userC.setTrustScore(50.0);

        userD = new AppUser();
        userD.setId(UUID.randomUUID());
        // Setup initial trust score differently to verify it gets overwritten
        userD.setTrustScore(20.0);
    }

    @Test
    void computeTrustScores_withVariousWinRates() {
        // Arrange
        when(userRepository.findAll(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(userA, userB, userC, userD), PageRequest.of(0, 250), 4));
        when(portfolioRepository.findByOwnerId(anyString())).thenReturn(List.of());

        when(analyticsService.calculateWinRate(userA.getId())).thenReturn(80.0);
        when(analyticsService.countResolvedPredictions(userA.getId())).thenReturn(20L);
        when(analyticsService.calculateWinRate(userB.getId())).thenReturn(40.0);
        when(analyticsService.countResolvedPredictions(userB.getId())).thenReturn(20L);
        when(analyticsService.calculateWinRate(userC.getId())).thenReturn(0.0);
        when(analyticsService.countResolvedPredictions(userC.getId())).thenReturn(0L);
        when(analyticsService.calculateWinRate(userD.getId())).thenReturn(100.0);
        when(analyticsService.countResolvedPredictions(userD.getId())).thenReturn(2L);

        trustScoreService.computeTrustScores();

        verify(userRepository, times(1)).saveAll(any(java.util.List.class));

        assertEquals(61.71, userA.getTrustScore(), 0.001);
        assertEquals(55.43, userB.getTrustScore(), 0.001);
        assertEquals(50.0, userC.getTrustScore(), 0.001);
        assertEquals(52.9, userD.getTrustScore(), 0.001);
    }

    @Test
    void computeTrustScores_capsLimits() {
        AppUser userExtremeUpper = new AppUser();
        userExtremeUpper.setId(UUID.randomUUID());

        AppUser userExtremeLower = new AppUser();
        userExtremeLower.setId(UUID.randomUUID());

        when(userRepository.findAll(PageRequest.of(0, 250))).thenReturn(
                new PageImpl<>(java.util.List.of(userExtremeUpper, userExtremeLower), PageRequest.of(0, 250), 2));
        when(portfolioRepository.findByOwnerId(anyString())).thenReturn(List.of());

        when(analyticsService.calculateWinRate(userExtremeUpper.getId())).thenReturn(150.0);
        when(analyticsService.countResolvedPredictions(userExtremeUpper.getId())).thenReturn(200L);
        when(analyticsService.calculateWinRate(userExtremeLower.getId())).thenReturn(1.0);
        when(analyticsService.countResolvedPredictions(userExtremeLower.getId())).thenReturn(200L);

        trustScoreService.computeTrustScores();

        assertEquals(70.58, userExtremeUpper.getTrustScore(), 0.001);
        assertEquals(49.63, userExtremeLower.getTrustScore(), 0.001);
    }

    @Test
    void computeTrustScores_processesAllPages() {
        List<AppUser> firstPageUsers = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            AppUser user = new AppUser();
            user.setId(UUID.randomUUID());
            firstPageUsers.add(user);
        }

        AppUser secondPageUser = new AppUser();
        secondPageUser.setId(UUID.randomUUID());

        when(userRepository.findAll(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(firstPageUsers, PageRequest.of(0, 250), 251));
        when(userRepository.findAll(PageRequest.of(1, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(secondPageUser), PageRequest.of(1, 250), 251));

        when(analyticsService.calculateWinRate(any(UUID.class))).thenReturn(50.0);
        when(analyticsService.countResolvedPredictions(any(UUID.class))).thenReturn(10L);
        when(portfolioRepository.findByOwnerId(anyString())).thenReturn(List.of());

        trustScoreService.computeTrustScores();

        verify(userRepository, times(1)).findAll(PageRequest.of(0, 250));
        verify(userRepository, times(1)).findAll(PageRequest.of(1, 250));
        verify(userRepository, times(2)).saveAll(any(java.util.List.class));
    }

    @Test
    void buildTrustScoreBreakdown_rewardsProfitablePortfoliosAndTrades() {
        UUID userId = UUID.randomUUID();
        Portfolio profitablePortfolio = Portfolio.builder()
                .id(UUID.randomUUID())
                .ownerId(userId.toString())
                .name("Profitable")
                .build();
        Portfolio losingPortfolio = Portfolio.builder()
                .id(UUID.randomUUID())
                .ownerId(userId.toString())
                .name("Losing")
                .build();

        when(analyticsService.calculateWinRate(userId)).thenReturn(75.0);
        when(analyticsService.countResolvedPredictions(userId)).thenReturn(12L);
        when(portfolioRepository.findByOwnerId(userId.toString())).thenReturn(List.of(profitablePortfolio, losingPortfolio));
        when(tradeActivityRepository.countProfitableRealizedTrades(List.of(profitablePortfolio.getId(), losingPortfolio.getId()))).thenReturn(6L);
        when(tradeActivityRepository.countLosingRealizedTrades(List.of(profitablePortfolio.getId(), losingPortfolio.getId()))).thenReturn(2L);
        when(tradeActivityRepository.sumRealizedPnl(List.of(profitablePortfolio.getId(), losingPortfolio.getId()))).thenReturn(BigDecimal.valueOf(4200));
        when(binanceService.getPrices()).thenReturn(java.util.Map.of("BTCUSDT", 50000.0));
        when(performanceCalculationService.getStartTimeForPeriod("ALL")).thenReturn(java.time.LocalDateTime.now());
        when(performanceCalculationService.calculateMetrics(eq(profitablePortfolio), any(), eq("ALL"), any()))
                .thenReturn(PerformanceCalculationService.PerformanceMetrics.builder()
                        .profitLoss(BigDecimal.valueOf(1800))
                        .returnPercentage(BigDecimal.valueOf(12.5))
                        .build());
        when(performanceCalculationService.calculateMetrics(eq(losingPortfolio), any(), eq("ALL"), any()))
                .thenReturn(PerformanceCalculationService.PerformanceMetrics.builder()
                        .profitLoss(BigDecimal.valueOf(-400))
                        .returnPercentage(BigDecimal.valueOf(-2.5))
                        .build());

        TrustScoreBreakdownResponse breakdown = trustScoreService.buildTrustScoreBreakdown(userId);

        assertEquals(75.0, breakdown.getPredictionWinRate(), 0.001);
        assertEquals(70.65, breakdown.getBlendedWinRate(), 0.001);
        assertEquals(12L, breakdown.getResolvedPredictionCount());
        assertEquals(75.0, breakdown.getTradeWinRate(), 0.001);
        assertEquals(8L, breakdown.getResolvedTradeCount());
        assertEquals(1, breakdown.getProfitablePortfolioCount());
        assertEquals(2, breakdown.getTotalPortfolioCount());
        assertEquals(50.0, breakdown.getPortfolioWinRate(), 0.001);
        assertEquals(5.0, breakdown.getAveragePortfolioReturn().doubleValue(), 0.001);
        assertTrue(trustScoreService.calculateTrustScore(breakdown) > 50.0);
    }

    @Test
    void buildTrustHistory_injectsCurrentPointWhenSnapshotsAreStale() {
        UUID userId = UUID.randomUUID();
        TrustScoreBreakdownResponse breakdown = TrustScoreBreakdownResponse.builder()
                .blendedWinRate(61.5)
                .resolvedPredictionCount(4)
                .resolvedTradeCount(8)
                .totalPortfolioCount(2)
                .build();

        when(trustScoreSnapshotRepository.findByUserIdOrderByCapturedAtDesc(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(
                        TrustScoreSnapshot.builder()
                                .userId(userId)
                                .trustScore(52.0)
                                .winRate(54.0)
                                .capturedAt(LocalDateTime.now().minusHours(2))
                                .build()));

        List<TrustScoreHistoryPointResponse> history = trustScoreService.buildTrustHistory(userId, breakdown, 58.4, 30);

        assertEquals(2, history.size());
        assertEquals(52.0, history.get(0).getTrustScore(), 0.001);
        assertEquals(58.4, history.get(1).getTrustScore(), 0.001);
        assertEquals(61.5, history.get(1).getWinRate(), 0.001);
    }
}
