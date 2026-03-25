package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.dto.TradeRequest;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.service.BinanceService;
import com.finance.core.service.CopyTradingService;
import com.finance.core.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Autowired
    private com.finance.core.repository.PortfolioItemRepository portfolioItemRepository;

    @Autowired
    private com.finance.core.repository.StrategyBotRepository strategyBotRepository;

    @Autowired
    private com.finance.core.repository.StrategyBotRunRepository strategyBotRunRepository;

    @Autowired
    private com.finance.core.repository.StrategyBotRunFillRepository strategyBotRunFillRepository;

    @Autowired
    private com.finance.core.repository.StrategyBotRunEventRepository strategyBotRunEventRepository;

    @Autowired
    private com.finance.core.repository.StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;

    @MockitoBean
    private BinanceService binanceService;

    @MockitoBean
    private CopyTradingService copyTradingService;

    @MockitoBean
    private TournamentService tournamentService;

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
        strategyBotRunEventRepository.deleteAll();
        strategyBotRunEquityPointRepository.deleteAll();
        strategyBotRunFillRepository.deleteAll();
        strategyBotRunRepository.deleteAll();
        strategyBotRepository.deleteAll();
        portfolioItemRepository.deleteAll();
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
    void testBuyMicroQuantity_shouldPersistQuantityWithoutRoundingToZero() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(new BigDecimal("0.001"));
        request.setLeverage(1);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(9950.0))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(0.001));

        PortfolioItem savedItem = portfolioItemRepository.findAll().get(0);
        assertEquals(0, new BigDecimal("0.001").compareTo(savedItem.getQuantity()));

        var trades = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(testPortfolio.getId());
        assertEquals(1, trades.size());
        assertEquals(0, new BigDecimal("0.001").compareTo(trades.get(0).getQuantity()));
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

    @Test
    void testBuy_whenPriceUnavailable_shouldReturnUnifiedErrorContract() throws Exception {
        when(binanceService.getPrices()).thenReturn(Map.of());

        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "trade-err-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trade-err-1"))
                .andExpect(jsonPath("$.code").value("price_not_available"))
                .andExpect(jsonPath("$.message").value("Price not available for symbol: BTCUSDT"))
                .andExpect(jsonPath("$.requestId").value("trade-err-1"));
    }

    @Test
    void testBuy_withInvalidPortfolioId_shouldReturnUnifiedErrorContract() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId("not-a-uuid");
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "trade-err-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trade-err-2"))
                .andExpect(jsonPath("$.code").value("portfolio_id_invalid"))
                .andExpect(jsonPath("$.message").value("Portfolio id must be a valid UUID"))
                .andExpect(jsonPath("$.requestId").value("trade-err-2"));
    }

    @Test
    void testBuy_withInvalidQuantity_shouldReturnUnifiedErrorContract() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.ZERO);
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "trade-err-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trade-err-3"))
                .andExpect(jsonPath("$.code").value("trade_quantity_invalid"))
                .andExpect(jsonPath("$.message").value("Trade quantity must be greater than 0"))
                .andExpect(jsonPath("$.requestId").value("trade-err-3"));
    }

    @Test
    void testBuy_withInvalidLeverage_shouldReturnUnifiedErrorContract() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(0);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "trade-err-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trade-err-4"))
                .andExpect(jsonPath("$.code").value("trade_leverage_invalid"))
                .andExpect(jsonPath("$.message").value("Trade leverage must be greater than 0"))
                .andExpect(jsonPath("$.requestId").value("trade-err-4"));
    }

    @Test
    void testBuy_withInvalidSide_shouldReturnUnifiedErrorContract() throws Exception {
        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("SWING");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "trade-err-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trade-err-5"))
                .andExpect(jsonPath("$.code").value("trade_side_invalid"))
                .andExpect(jsonPath("$.message").value("Trade side must be LONG or SHORT"))
                .andExpect(jsonPath("$.requestId").value("trade-err-5"));
    }

    @Test
    void testSell_withoutSide_whenBothLongAndShortExist_shouldRequireExplicitSide() throws Exception {
        PortfolioItem longItem = portfolioItemRepository.save(PortfolioItem.builder()
                .portfolio(testPortfolio)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("1.0"))
                .averagePrice(new BigDecimal("50000"))
                .leverage(1)
                .side("LONG")
                .build());
        PortfolioItem shortItem = portfolioItemRepository.save(PortfolioItem.builder()
                .portfolio(testPortfolio)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("1.0"))
                .averagePrice(new BigDecimal("49000"))
                .leverage(1)
                .side("SHORT")
                .build());
        testPortfolio.getItems().add(longItem);
        testPortfolio.getItems().add(shortItem);
        portfolioRepository.save(testPortfolio);

        mockMvc.perform(post("/api/v1/trade/sell")
                        .header("X-Request-Id", "trade-err-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "portfolioId", testPortfolio.getId().toString(),
                                "symbol", "BTCUSDT",
                                "quantity", 0.5))))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "trade-err-6"))
                .andExpect(jsonPath("$.code").value("trade_side_required"))
                .andExpect(jsonPath("$.message").value("Trade side is required when multiple positions exist for symbol"))
                .andExpect(jsonPath("$.requestId").value("trade-err-6"));
    }

    @Test
    void testSell_withExplicitSide_shouldOnlyCloseMatchingPosition() throws Exception {
        PortfolioItem longItem = portfolioItemRepository.save(PortfolioItem.builder()
                .portfolio(testPortfolio)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("1.0"))
                .averagePrice(new BigDecimal("50000"))
                .leverage(1)
                .side("LONG")
                .build());
        PortfolioItem shortItem = portfolioItemRepository.save(PortfolioItem.builder()
                .portfolio(testPortfolio)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("1.0"))
                .averagePrice(new BigDecimal("49000"))
                .leverage(1)
                .side("SHORT")
                .build());
        testPortfolio.getItems().add(longItem);
        testPortfolio.getItems().add(shortItem);
        portfolioRepository.save(testPortfolio);

        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(new BigDecimal("0.25"));
        request.setSide("SHORT");

        mockMvc.perform(post("/api/v1/trade/sell")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));

        PortfolioItem updatedLongItem = portfolioItemRepository.findById(longItem.getId()).orElseThrow();
        PortfolioItem updatedShortItem = portfolioItemRepository.findById(shortItem.getId()).orElseThrow();

        assertEquals(0, new BigDecimal("1.0").compareTo(updatedLongItem.getQuantity()));
        assertEquals(0, new BigDecimal("0.75").compareTo(updatedShortItem.getQuantity()));
    }

    @Test
    void testBuy_whenPriceLookupThrows_shouldReturnSanitizedTradeError() throws Exception {
        when(binanceService.getPrices()).thenThrow(new RuntimeException("provider timeout"));

        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .header("X-Request-Id", "trade-err-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "trade-err-7"))
                .andExpect(jsonPath("$.code").value("trade_price_lookup_failed"))
                .andExpect(jsonPath("$.message").value("Failed to load trade price"))
                .andExpect(jsonPath("$.requestId").value("trade-err-7"));
    }

    @Test
    void testBuy_whenCopyAndTournamentSideEffectsFail_shouldStillSucceed() throws Exception {
        doThrow(new RuntimeException("tournament push failed")).when(tournamentService).notifyTournamentOfTrade(any());
        doThrow(new RuntimeException("copy sync failed")).when(copyTradingService)
                .replicateBuy(eq(testPortfolio.getId()), any(TradeRequest.class), any(BigDecimal.class));

        TradeRequest request = new TradeRequest();
        request.setPortfolioId(testPortfolio.getId().toString());
        request.setSymbol("BTCUSDT");
        request.setQuantity(BigDecimal.valueOf(0.1));
        request.setLeverage(10);
        request.setSide("LONG");

        mockMvc.perform(post("/api/v1/trade/buy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(9500.0));

        assertEquals(1, tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(testPortfolio.getId()).size());
        verify(tournamentService).notifyTournamentOfTrade(any());
        verify(copyTradingService).replicateBuy(eq(testPortfolio.getId()), any(TradeRequest.class), any(BigDecimal.class));
    }
}
