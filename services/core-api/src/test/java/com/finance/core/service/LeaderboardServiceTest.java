package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.AppUser;
import com.finance.core.dto.AccountLeaderboardEntry;
import com.finance.core.dto.LeaderboardEntry;
import com.finance.core.dto.TrustScoreBreakdownResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceTest {

        @Mock
        private PortfolioRepository portfolioRepository;
        @Mock
        private PerformanceCalculationService performanceCalculationService;
        @Mock
        private BinanceService binanceService;
        @Mock
        private CacheService cacheService;
        @Mock
        private UserRepository userRepository;
        @Mock
        private TrustScoreService trustScoreService;

        @InjectMocks
        private LeaderboardService leaderboardService;

        private Portfolio portfolioA;
        @BeforeEach
        void setUp() {
                PortfolioItem item = PortfolioItem.builder()
                                .id(UUID.randomUUID())
                                .symbol("BTCUSDT")
                                .averagePrice(BigDecimal.valueOf(50000))
                                .quantity(BigDecimal.ONE)
                                .side("LONG")
                                .leverage(1)
                                .build();

                portfolioA = Portfolio.builder()
                                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                                .name("Alpha Portfolio")
                                .ownerId("11111111-1111-1111-1111-111111111112")
                                .balance(BigDecimal.valueOf(100000))
                                .items(List.of(item))
                                .visibility(Portfolio.Visibility.PUBLIC)
                                .build();
        }

        @Test
        void getLeaderboard_returnPercentageDescending_shouldRankCorrectly() {
                Pageable pageable = PageRequest.of(0, 10);

                Set<ZSetOperations.TypedTuple<Object>> mockRange = new LinkedHashSet<>();
                mockRange.add(new DefaultTypedTuple<>(portfolioA.getId().toString(), 10.0));

                when(cacheService.zRevRangeWithScores(eq("leaderboard_portfolios:ALL"), anyLong(), anyLong()))
                                .thenReturn(mockRange);
                when(cacheService.zCard(eq("leaderboard_portfolios:ALL"))).thenReturn(1L);
                when(portfolioRepository.findByIdInAndVisibility(anyList(), eq(Portfolio.Visibility.PUBLIC)))
                                .thenReturn(List.of(portfolioA));
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 55000.0));
                when(performanceCalculationService.getStartTimeForPeriod("ALL"))
                                .thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
                when(performanceCalculationService.calculateMetrics(eq(portfolioA), any(LocalDateTime.class), eq("ALL"),
                                anyMap()))
                                .thenReturn(PerformanceCalculationService.PerformanceMetrics.builder()
                                                .currentEquity(BigDecimal.valueOf(110000))
                                                .startEquity(BigDecimal.valueOf(100000))
                                                .profitLoss(BigDecimal.valueOf(10000))
                                                .returnPercentage(BigDecimal.valueOf(10.0))
                                                .build());

                // We also need to mock userRepository to return the owner
                AppUser user = AppUser.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111112"))
                                .username("traderX").build();
                when(userRepository.findAllById(any())).thenReturn(List.of(user));

                Page<LeaderboardEntry> page = leaderboardService.getLeaderboard(
                                "ALL",
                                "RETURN_PERCENTAGE",
                                "DESC",
                                pageable);

                assertNotNull(page);
                assertEquals(1, page.getTotalElements());
                assertEquals("Alpha Portfolio", page.getContent().get(0).getPortfolioName());
                assertEquals(0, BigDecimal.valueOf(10.0).compareTo(page.getContent().get(0).getReturnPercentage()));
        }

        @Test
        void getLeaderboard_profitAscending_shouldUseProfitCacheAndAscendingRange() {
                Pageable pageable = PageRequest.of(0, 10);

                Set<ZSetOperations.TypedTuple<Object>> mockRange = new LinkedHashSet<>();
                mockRange.add(new DefaultTypedTuple<>(portfolioA.getId().toString(), 1000.0));

                when(cacheService.zRangeWithScores(eq("leaderboard_portfolios_profit:ALL"), anyLong(), anyLong()))
                                .thenReturn(mockRange);
                when(cacheService.zCard(eq("leaderboard_portfolios_profit:ALL"))).thenReturn(1L);
                when(portfolioRepository.findByIdInAndVisibility(anyList(), eq(Portfolio.Visibility.PUBLIC)))
                                .thenReturn(List.of(portfolioA));
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 55000.0));
                when(performanceCalculationService.getStartTimeForPeriod("ALL"))
                                .thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
                when(performanceCalculationService.calculateMetrics(eq(portfolioA), any(LocalDateTime.class), eq("ALL"),
                                anyMap()))
                                .thenReturn(PerformanceCalculationService.PerformanceMetrics.builder()
                                                .currentEquity(BigDecimal.valueOf(101000))
                                                .startEquity(BigDecimal.valueOf(100000))
                                                .profitLoss(BigDecimal.valueOf(1000))
                                                .returnPercentage(BigDecimal.ONE)
                                                .build());
                AppUser user = AppUser.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111112"))
                                .username("traderX").build();
                when(userRepository.findAllById(any())).thenReturn(List.of(user));

                Page<LeaderboardEntry> page = leaderboardService.getLeaderboard(
                                "ALL",
                                "PROFIT_LOSS",
                                "ASC",
                                pageable);

                assertEquals(1, page.getTotalElements());
                verify(cacheService).zRangeWithScores(eq("leaderboard_portfolios_profit:ALL"), anyLong(), anyLong());
                verify(cacheService, never()).zRevRangeWithScores(eq("leaderboard_portfolios_profit:ALL"), anyLong(),
                                anyLong());
        }

        @Test
        void refreshLeaderboardJob_shouldClearStaleKeysAndProcessPagedPortfolios() {
                Page<UUID> firstPage = new PageImpl<>(List.of(portfolioA.getId()), PageRequest.of(0, 250), 1);

                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 55000.0));
                when(portfolioRepository.findIdsByVisibility(eq(Portfolio.Visibility.PUBLIC), eq(PageRequest.of(0, 250))))
                                .thenReturn(firstPage);
                when(portfolioRepository.findByIdInAndVisibility(eq(List.of(portfolioA.getId())), eq(Portfolio.Visibility.PUBLIC)))
                                .thenReturn(List.of(portfolioA));
                when(performanceCalculationService.getStartTimeForPeriod(anyString()))
                                .thenReturn(LocalDateTime.of(2026, 2, 1, 0, 0));
                when(performanceCalculationService.calculateMetrics(eq(portfolioA), any(LocalDateTime.class), anyString(),
                                anyMap()))
                                .thenReturn(PerformanceCalculationService.PerformanceMetrics.builder()
                                                .currentEquity(BigDecimal.valueOf(101000))
                                                .startEquity(BigDecimal.valueOf(100000))
                                                .profitLoss(BigDecimal.valueOf(1000))
                                                .returnPercentage(BigDecimal.ONE)
                                                .build());

                leaderboardService.refreshLeaderboardJob();

                verify(cacheService).delete("leaderboard_portfolios:1D");
                verify(cacheService).delete("leaderboard_portfolios:1W");
                verify(cacheService).delete("leaderboard_portfolios:1M");
                verify(cacheService).delete("leaderboard_portfolios:ALL");
                verify(cacheService).delete("leaderboard_portfolios_profit:1D");
                verify(cacheService).delete("leaderboard_portfolios_profit:1W");
                verify(cacheService).delete("leaderboard_portfolios_profit:1M");
                verify(cacheService).delete("leaderboard_portfolios_profit:ALL");
                verify(cacheService).delete("leaderboard_accounts:1D");
                verify(cacheService).delete("leaderboard_accounts:1W");
                verify(cacheService).delete("leaderboard_accounts:1M");
                verify(cacheService).delete("leaderboard_accounts:ALL");
                verify(cacheService).delete("leaderboard_accounts_profit:1D");
                verify(cacheService).delete("leaderboard_accounts_profit:1W");
                verify(cacheService).delete("leaderboard_accounts_profit:1M");
                verify(cacheService).delete("leaderboard_accounts_profit:ALL");

                String member = portfolioA.getId().toString();
                String ownerMember = portfolioA.getOwnerId();
                verify(cacheService).zAdd(eq("leaderboard_portfolios:1D"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios:1W"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios:1M"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios:ALL"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios_profit:1D"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios_profit:1W"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios_profit:1M"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_portfolios_profit:ALL"), eq(member), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts:1D"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts:1W"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts:1M"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts:ALL"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts_profit:1D"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts_profit:1W"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts_profit:1M"), eq(ownerMember), anyDouble());
                verify(cacheService).zAdd(eq("leaderboard_accounts_profit:ALL"), eq(ownerMember), anyDouble());

                verify(cacheService, times(8)).expire(startsWith("leaderboard_portfolios"), any());
        }

        @Test
        void getAccountLeaderboard_shouldAggregatePublicPortfoliosPerOwnerForTrustSort() {
                Pageable pageable = PageRequest.of(0, 10);
                UUID ownerId = UUID.fromString(portfolioA.getOwnerId());
                when(portfolioRepository.findByVisibility(eq(Portfolio.Visibility.PUBLIC)))
                                .thenReturn(List.of(portfolioA));
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 55000.0));
                when(performanceCalculationService.getStartTimeForPeriod("ALL"))
                                .thenReturn(LocalDateTime.of(2026, 1, 1, 0, 0));
                when(performanceCalculationService.calculateMetrics(eq(portfolioA), any(LocalDateTime.class), eq("ALL"),
                                anyMap()))
                                .thenReturn(PerformanceCalculationService.PerformanceMetrics.builder()
                                                .currentEquity(BigDecimal.valueOf(110000))
                                                .startEquity(BigDecimal.valueOf(100000))
                                                .profitLoss(BigDecimal.valueOf(10000))
                                                .returnPercentage(BigDecimal.valueOf(10.0))
                                                .build());

                AppUser user = AppUser.builder()
                                .id(ownerId)
                                .username("traderX")
                                .displayName("Trader X")
                                .build();
                when(userRepository.findAllById(any())).thenReturn(List.of(user));
                when(trustScoreService.buildTrustScoreBreakdown(ownerId)).thenReturn(TrustScoreBreakdownResponse.builder()
                                .blendedWinRate(68.5)
                                .build());
                when(trustScoreService.calculateTrustScore(any())).thenReturn(63.4);

                Page<AccountLeaderboardEntry> page = leaderboardService.getAccountLeaderboard(
                                "ALL",
                                "TRUST_SCORE",
                                "DESC",
                                pageable);

                assertEquals(1, page.getTotalElements());
                assertEquals("Trader X", page.getContent().get(0).getOwnerName());
                assertEquals(1, page.getContent().get(0).getPublicPortfolioCount());
                assertEquals(63.4, page.getContent().get(0).getTrustScore(), 0.001);
                assertEquals(68.5, page.getContent().get(0).getWinRate(), 0.001);
        }
}
