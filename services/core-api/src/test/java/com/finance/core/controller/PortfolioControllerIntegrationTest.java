package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.TradeActivity;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeActivityRepository tradeActivityRepository;

    @Autowired
    private com.finance.core.repository.ActivityEventRepository activityEventRepository;

    @Autowired
    private com.finance.core.repository.NotificationRepository notificationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private BinanceService binanceService;

    private Portfolio portfolio;
    private final String ownerId = "owner-items-test";

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        activityEventRepository.deleteAll();
        tradeActivityRepository.deleteAll();
        portfolioRepository.deleteAll();

        portfolio = Portfolio.builder()
                .name("Holdings Visibility Test")
                .ownerId(ownerId)
                .balance(BigDecimal.valueOf(10000))
                .build();
        portfolio = portfolioRepository.save(portfolio);

        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
    }

    @Test
    void listPortfolios_shouldIncludeOpenItemsAfterTrade() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(portfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/portfolios")
                .param("ownerId", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].items", hasSize(1)))
                .andExpect(jsonPath("$.content[0].items[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.content[0].items[0].side").value("LONG"));
    }

    @Test
    void getPortfolioHistory_shouldNormalizeLegacyBuyNullRealizedPnlToZero() throws Exception {
        tradeActivityRepository.save(TradeActivity.builder()
                .portfolioId(portfolio.getId())
                .symbol("BTCUSDT")
                .type("BUY")
                .side("LONG")
                .quantity(BigDecimal.valueOf(0.1))
                .price(BigDecimal.valueOf(50000))
                .realizedPnl(null)
                .build());

        mockMvc.perform(get("/api/v1/portfolios/{id}/history", portfolio.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("BUY"))
                .andExpect(jsonPath("$[0].realizedPnl").value(0));
    }
}
