package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.TradeRequest;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TradeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeActivityRepository tradeActivityRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private BinanceService binanceService;

    @Autowired
    private com.finance.core.repository.ActivityEventRepository activityEventRepository;

    @Autowired
    private com.finance.core.repository.NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Portfolio testPortfolio;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        activityEventRepository.deleteAll();
        tradeActivityRepository.deleteAll();
        portfolioRepository.deleteAll();

        testPortfolio = Portfolio.builder()
                .name("Integration Portfolio")
                .ownerId("user-123")
                .balance(BigDecimal.valueOf(10000))
                .build();
        testPortfolio = portfolioRepository.save(testPortfolio);

        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
    }

    @Test
    void testBuyLong() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        // Cost = 50,000 * 0.1 = 5,000. Margin = 5,000 / 10 = 500.
        mockMvc.perform(post("/api/v1/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(9500.0))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].side").value("LONG"));
    }

    @Test
    void testBuyLong_shouldPersistZeroRealizedPnlTradeActivity() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        var trades = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(testPortfolio.getId());
        assertEquals(1, trades.size());
        assertEquals("BUY", trades.get(0).getType());
        assertNotNull(trades.get(0).getRealizedPnl());
        assertEquals(0, BigDecimal.ZERO.compareTo(trades.get(0).getRealizedPnl()));
    }

    @Test
    void testBuyShort() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(5);
        request.setSide("SHORT");

        // Margin = 5000 / 5 = 1000
        mockMvc.perform(post("/api/v1/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(9000.0))
                .andExpect(jsonPath("$.items[0].side").value("SHORT"));
    }
}
