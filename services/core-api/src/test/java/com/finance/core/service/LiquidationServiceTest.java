package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.TradeActivity;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiquidationServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private TradeActivityRepository tradeActivityRepository;
    @Mock
    private BinanceService binanceService;

    @InjectMocks
    private LiquidationService liquidationService;

    private Portfolio portfolio;
    private PortfolioItem leveragedItem;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio();
        portfolio.setId(UUID.randomUUID());
        portfolio.setName("Test Portfolio");
        portfolio.setItems(new ArrayList<>());

        leveragedItem = new PortfolioItem();
        leveragedItem.setId(UUID.randomUUID());
        leveragedItem.setSymbol("BTCUSDT");
        leveragedItem.setAveragePrice(BigDecimal.valueOf(10000));
        leveragedItem.setQuantity(BigDecimal.valueOf(1));
        leveragedItem.setLeverage(10);
        leveragedItem.setSide("LONG");
        leveragedItem.setPortfolio(portfolio);

        portfolio.getItems().add(leveragedItem);
    }

    @Test
    void testLongLiquidation() {
        // Entry: 10,000, Lev: 10x. Liq Price = 10,000 * (1 - 1/10) = 9,000
        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 8900.0));
        when(portfolioRepository.findAllBy(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(portfolio), PageRequest.of(0, 250), 1));

        liquidationService.monitorLiquidations();

        verify(portfolioItemRepository, times(1)).delete(leveragedItem);
        verify(tradeActivityRepository, times(1)).save(any(TradeActivity.class));
        verify(portfolioRepository, times(1)).save(portfolio);
    }

    @Test
    void testShortLiquidation() {
        leveragedItem.setSide("SHORT");
        // Entry: 10,000, Lev: 10x. Liq Price = 10,000 * (1 + 1/10) = 11,000
        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 11100.0));
        when(portfolioRepository.findAllBy(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(portfolio), PageRequest.of(0, 250), 1));

        liquidationService.monitorLiquidations();

        verify(portfolioItemRepository, times(1)).delete(leveragedItem);
        verify(tradeActivityRepository, times(1)).save(any(TradeActivity.class));
    }

    @Test
    void testNoLiquidation() {
        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 9500.0));
        when(portfolioRepository.findAllBy(PageRequest.of(0, 250)))
                .thenReturn(new PageImpl<>(java.util.List.of(portfolio), PageRequest.of(0, 250), 1));

        liquidationService.monitorLiquidations();

        verify(portfolioItemRepository, never()).delete(any());
    }

    @Test
    void testSkipsWhenNoMarketPrices() {
        when(binanceService.getPrices()).thenReturn(Map.of());

        liquidationService.monitorLiquidations();

        verify(portfolioRepository, never()).findAllBy(any());
        verifyNoInteractions(portfolioItemRepository, tradeActivityRepository);
    }
}
