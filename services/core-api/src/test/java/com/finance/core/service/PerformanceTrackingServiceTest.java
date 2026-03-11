package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceTrackingServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioSnapshotRepository snapshotRepository;
    @Mock
    private BinanceService binanceService;

    @InjectMocks
    private PerformanceTrackingService performanceTrackingService;

    private Portfolio portfolio;
    private PortfolioItem longItem;
    private PortfolioItem shortItem;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setBalance(BigDecimal.valueOf(10000));
        portfolio.setItems(new ArrayList<>());

        longItem = new PortfolioItem();
        longItem.setSymbol("BTCUSDT");
        longItem.setQuantity(BigDecimal.valueOf(1));
        longItem.setAveragePrice(BigDecimal.valueOf(50000));
        longItem.setSide("LONG");
        longItem.setPortfolio(portfolio);

        shortItem = new PortfolioItem();
        shortItem.setSymbol("ETHUSDT");
        shortItem.setQuantity(BigDecimal.valueOf(10));
        shortItem.setAveragePrice(BigDecimal.valueOf(3000));
        shortItem.setSide("SHORT");
        shortItem.setPortfolio(portfolio);
    }

    @Test
    void testCaptureSnapshots() {
        // Setup portfolio with Long BTC and Short ETH
        portfolio.getItems().add(longItem);
        portfolio.getItems().add(shortItem);

        // Mock Prices: BTC up, ETH down
        // BTC: 50000 -> 52000 (+2000 PNL)
        // ETH: 3000 -> 2900 (+100 per unit * 10 = +1000 PNL)
        when(binanceService.getPrices()).thenReturn(Map.of(
                "BTCUSDT", 52000.0,
                "ETHUSDT", 2900.0));
        when(portfolioRepository.findAllIds(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(List.of(portfolio.getId()), PageRequest.of(0, 250), 1));
        when(portfolioRepository.findByIdIn(List.of(portfolio.getId())))
                .thenReturn(List.of(portfolio));

        performanceTrackingService.captureSnapshots();

        ArgumentCaptor<PortfolioSnapshot> captor = ArgumentCaptor.forClass(PortfolioSnapshot.class);
        verify(snapshotRepository, times(1)).save(captor.capture());

        PortfolioSnapshot snapshot = captor.getValue();

        // Balance: 10000
        // Total Margin: 50000 (BTC) + 30000 (ETH) = 80000
        // BTC PNL: (52000 - 50000) * 1 = 2000
        // ETH PNL: (3000 - 2900) * 10 = 1000
        // Total Equity = 10000 + 80000 + 2000 + 1000 = 93000
        assertEquals(0, BigDecimal.valueOf(93000).compareTo(snapshot.getTotalEquity()));
    }

    @Test
    void testCaptureSnapshotsNoPrices() {
        when(binanceService.getPrices()).thenReturn(Collections.emptyMap());

        performanceTrackingService.captureSnapshots();

        verify(snapshotRepository, never()).save(any());
        verify(portfolioRepository, never()).findAllIds(any());
    }

    @Test
    void testCaptureSnapshotsProcessesAllPages() {
        java.util.List<Portfolio> firstPagePortfolios = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            Portfolio p = new Portfolio();
            p.setId(UUID.randomUUID());
            p.setBalance(BigDecimal.valueOf(5000));
            p.setItems(new ArrayList<>());
            firstPagePortfolios.add(p);
        }

        Portfolio secondPagePortfolio = new Portfolio();
        secondPagePortfolio.setId(UUID.randomUUID());
        secondPagePortfolio.setBalance(BigDecimal.valueOf(5000));
        secondPagePortfolio.setItems(new ArrayList<>());

        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
        List<UUID> firstIds = firstPagePortfolios.stream().map(Portfolio::getId).toList();
        when(portfolioRepository.findAllIds(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(firstIds, PageRequest.of(0, 250), 251));
        when(portfolioRepository.findByIdIn(firstIds))
                .thenReturn(firstPagePortfolios);
        when(portfolioRepository.findAllIds(PageRequest.of(1, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(secondPagePortfolio.getId()), PageRequest.of(1, 250), 251));
        when(portfolioRepository.findByIdIn(List.of(secondPagePortfolio.getId())))
                .thenReturn(List.of(secondPagePortfolio));

        performanceTrackingService.captureSnapshots();

        verify(portfolioRepository, times(1)).findAllIds(PageRequest.of(0, 250));
        verify(portfolioRepository, times(1)).findAllIds(PageRequest.of(1, 250));
        verify(snapshotRepository, times(251)).save(any(PortfolioSnapshot.class));
    }

    @Test
    void calculateTotalEquity_shouldPreserveMarginWhenCurrentPriceMissing() {
        portfolio.getItems().add(longItem);

        BigDecimal totalEquity = PerformanceTrackingService.calculateTotalEquity(portfolio, Collections.emptyMap());

        assertEquals(0, BigDecimal.valueOf(60000).compareTo(totalEquity));
    }
}
