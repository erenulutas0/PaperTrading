package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.repository.PortfolioSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceCalculationServiceTest {

        @Mock
        private PortfolioSnapshotRepository snapshotRepository;

        @Mock
        private BinanceService binanceService;

        @InjectMocks
        private PerformanceCalculationService performanceCalculationService;

        private Portfolio testPortfolio;
        private PortfolioSnapshot yesterdaySnapshot;

        @BeforeEach
        void setUp() {
                // Initial setup mimicking a $100k total equity portfolio
                // $50k in cash, $50k used as margin for 1 BTC (1x leverage)
                testPortfolio = Portfolio.builder()
                                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                                .name("Test Portfolio")
                                .ownerId("user-1")
                                .balance(BigDecimal.valueOf(50000))
                                .items(List.of(
                                                PortfolioItem.builder()
                                                                .id(UUID.randomUUID())
                                                                .symbol("BTCUSDT")
                                                                .quantity(BigDecimal.ONE)
                                                                .averagePrice(BigDecimal.valueOf(50000))
                                                                .leverage(1)
                                                                .side("LONG")
                                                                .build()))
                                .build();

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime yesterday = now.minusDays(1);

                // Snapshot also says $100k total equity yesterday
                yesterdaySnapshot = PortfolioSnapshot.builder()
                                .portfolioId(testPortfolio.getId())
                                .totalEquity(BigDecimal.valueOf(100000))
                                .timestamp(yesterday)
                                .build();
        }

        @Test
        void testCalculateReturn_ROE_PriceUp() {
                // Snapshot: $100k. Current: BTC price $51k (+1k profit).
                // Total Margin: $50k. Total Equity: $50k + $50k + $1k = $101k.
                // P/L: $1k. Return: $1k / $50k (Margin) = 2%
                when(snapshotRepository.findFirstByPortfolioIdAndTimestampLessThanEqualOrderByTimestampDesc(
                                eq(testPortfolio.getId()), any()))
                                .thenReturn(java.util.Optional.of(yesterdaySnapshot));

                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 51000.0));

                BigDecimal returnPct = performanceCalculationService.calculateReturn(testPortfolio, "1D");

                assertEquals(0, BigDecimal.valueOf(2.0).compareTo(returnPct));
        }

        @Test
        void testCalculateReturn_ROE_PriceDown() {
                // Snapshot: $100k. Current: BTC price $45k (-5k loss).
                // Equity: $50k + $50k - $5k = $95k. P/L: -$5k.
                // Return: -$5k / $50k = -10%
                when(snapshotRepository.findFirstByPortfolioIdAndTimestampLessThanEqualOrderByTimestampDesc(
                                any(), any()))
                                .thenReturn(java.util.Optional.of(yesterdaySnapshot));
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 45000.0));

                BigDecimal returnPct = performanceCalculationService.calculateReturn(testPortfolio, "1D");

                assertEquals(0, BigDecimal.valueOf(-10.0).compareTo(returnPct));
        }

        @Test
        void testCalculateReturn_Leveraged_10x() {
                // 10x leverage test
                // Balance $99k, Margin $1k (0.1 BTC at $100k size? No, let's keep it simple)
                // $90k balance, $10k margin, 2 BTC at $50k (10x leverage)
                testPortfolio = Portfolio.builder()
                                .balance(BigDecimal.valueOf(90000))
                                .items(List.of(
                                                PortfolioItem.builder()
                                                                .symbol("BTCUSDT").quantity(BigDecimal.valueOf(2))
                                                                .averagePrice(BigDecimal.valueOf(50000)).leverage(10)
                                                                .side("LONG").build()))
                                .build();

                // Price $50k -> $51k (+2% move). Profit = $1k * 2 = $2k.
                // Margin was $10k. Return = $2k / $10k = 20%.
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 51000.0));

                BigDecimal returnPct = performanceCalculationService.calculateReturn(testPortfolio, "ALL");

                assertEquals(0, BigDecimal.valueOf(20.0).compareTo(returnPct));
        }

        @Test
        void testCalculateReturn_ROE_MultipleItems() {
                // Balance: $85k. Margin: $15k ($10k BTC + $5k ETH)
                // BTC: 2 units at $50k (10x lev) = $100k size, $10k margin
                // ETH: 25 units at $2000 (10x lev) = $50k size, $5k margin
                testPortfolio = Portfolio.builder()
                                .balance(BigDecimal.valueOf(85000))
                                .items(List.of(
                                                PortfolioItem.builder()
                                                                .symbol("BTCUSDT").quantity(BigDecimal.valueOf(2))
                                                                .averagePrice(BigDecimal.valueOf(50000)).leverage(10)
                                                                .side("LONG").build(),
                                                PortfolioItem.builder()
                                                                .symbol("ETHUSDT").quantity(BigDecimal.valueOf(25))
                                                                .averagePrice(BigDecimal.valueOf(2000)).leverage(10)
                                                                .side("SHORT").build()))
                                .build();

                // BTC: $50k -> $51k (+$2000)
                // ETH: $2000 -> $2040 (Price up 2% on SHORT = -$1000)
                // Total P/L: $2000 - $1000 = $1000
                // Denominator (Margin): $15,000
                // Return: 1000 / 15000 = 6.67%
                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 51000.0, "ETHUSDT", 2040.0));

                BigDecimal returnPct = performanceCalculationService.calculateReturn(testPortfolio, "ALL");

                assertEquals(0, BigDecimal.valueOf(6.67).compareTo(returnPct.setScale(2, RoundingMode.HALF_UP)));
        }

        @Test
        void testCalculateReturn_ROI_NoTrades() {
                // No active trades. Equity $105k. Base $100k. ROI (Equity-based).
                // Margin = 0, so denominator = baseEquity ($100k)
                testPortfolio = Portfolio.builder()
                                .balance(BigDecimal.valueOf(105000))
                                .items(List.of())
                                .build();
                when(binanceService.getPrices()).thenReturn(Map.of());

                BigDecimal returnPct = performanceCalculationService.calculateReturn(testPortfolio, "ALL");

                assertEquals(0, BigDecimal.valueOf(5.0).compareTo(returnPct));
        }

        @Test
        void testCalculateReturn_WhenMarketPriceUnavailable_ShouldPreserveEntryEquity() {
                when(binanceService.getPrices()).thenReturn(Map.of());

                BigDecimal returnPct = performanceCalculationService.calculateReturn(testPortfolio, "ALL");

                assertEquals(0, BigDecimal.ZERO.compareTo(returnPct));
        }

        @Test
        void testGetStartTime_Various() {
                assertNotNull(performanceCalculationService.getStartTimeForPeriod("1D"));
                assertNotNull(performanceCalculationService.getStartTimeForPeriod("1W"));
                assertNotNull(performanceCalculationService.getStartTimeForPeriod("ALL"));
        }
}
