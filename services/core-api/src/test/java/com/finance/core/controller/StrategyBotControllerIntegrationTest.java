package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StrategyBotControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StrategyBotRepository strategyBotRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private StrategyBotRunRepository strategyBotRunRepository;

    private UUID userId;
    private Portfolio linkedPortfolio;

    @BeforeEach
    void setUp() {
        strategyBotRunRepository.deleteAll();
        strategyBotRepository.deleteAll();
        portfolioRepository.deleteAll();
        userId = UUID.randomUUID();
        linkedPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Bot Portfolio")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());
    }

    @Test
    void createAndListStrategyBots_shouldReturnPagedDraftBots() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "BTC Trend Draft");
        request.put("description", "RSI + breakout confirmation");
        request.put("linkedPortfolioId", linkedPortfolio.getId());
        request.put("market", "CRYPTO");
        request.put("symbol", "BTCUSDT");
        request.put("timeframe", "1H");
        request.put("entryRules", Map.of("all", java.util.List.of("price_above_ma_50", "rsi_above_55")));
        request.put("exitRules", Map.of("any", java.util.List.of("price_below_ma_20", "stop_loss_hit")));
        request.put("maxPositionSizePercent", 25);
        request.put("stopLossPercent", 3.5);
        request.put("takeProfitPercent", 8);
        request.put("cooldownMinutes", 30);
        request.put("status", "DRAFT");

        mockMvc.perform(post("/api/v1/strategy-bots")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BTC Trend Draft"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.botKind").value("RULE_BASED"))
                .andExpect(jsonPath("$.linkedPortfolioId").value(linkedPortfolio.getId().toString()))
                .andExpect(jsonPath("$.entryRules.all", hasSize(2)));

        mockMvc.perform(get("/api/v1/strategy-bots")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void updateStrategyBot_shouldAllowReadyStatusAndRuleChanges() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("ETH Draft")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{\"all\":[\"macd_cross\"]}")
                .exitRules("{\"any\":[\"stop_loss_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(15)
                .status(StrategyBot.Status.DRAFT)
                .build());

        Map<String, Object> request = Map.of(
                "status", "READY",
                "takeProfitPercent", 10,
                "entryRules", Map.of("all", java.util.List.of("macd_cross", "volume_breakout")));

        mockMvc.perform(put("/api/v1/strategy-bots/" + bot.getId())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.takeProfitPercent").value(10))
                .andExpect(jsonPath("$.entryRules.all", hasSize(2)));
    }

    @Test
    void createStrategyBot_shouldRejectUnownedLinkedPortfolio() throws Exception {
        Portfolio otherPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Other Portfolio")
                .ownerId(UUID.randomUUID().toString())
                .balance(new BigDecimal("50000"))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());

        Map<String, Object> request = Map.of(
                "name", "Bad Link",
                "linkedPortfolioId", otherPortfolio.getId(),
                "market", "CRYPTO",
                "symbol", "SOLUSDT",
                "timeframe", "15M",
                "entryRules", Map.of(),
                "exitRules", Map.of(),
                "maxPositionSizePercent", 15);

        mockMvc.perform(post("/api/v1/strategy-bots")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("linked_portfolio_not_found"));
    }

    @Test
    void deleteStrategyBot_shouldRemoveOwnedBot() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .name("Delete Me")
                .market("CRYPTO")
                .symbol("BNBUSDT")
                .timeframe("1D")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("10"))
                .cooldownMinutes(0)
                .status(StrategyBot.Status.DRAFT)
                .build());

        mockMvc.perform(delete("/api/v1/strategy-bots/" + bot.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("strategy_bot_not_found"));
    }
}
