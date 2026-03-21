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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StrategyBotRunControllerIntegrationTest {

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
                .name("Run Portfolio")
                .ownerId(userId.toString())
                .balance(new BigDecimal("120000"))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());
    }

    @Test
    void requestBacktestRun_shouldCreateQueuedRunAndReturnItInHistory() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Momentum Ready")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{\"all\":[\"rsi_above_55\"]}")
                .exitRules("{\"any\":[\"stop_loss_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("25"))
                .cooldownMinutes(30)
                .status(StrategyBot.Status.READY)
                .build());

        Map<String, Object> request = Map.of(
                "runMode", "BACKTEST",
                "initialCapital", 50000,
                "fromDate", "2026-01-01",
                "toDate", "2026-03-01");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runMode").value("BACKTEST"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.effectiveInitialCapital").value(50000))
                .andExpect(jsonPath("$.summary.phase").value("queued"))
                .andExpect(jsonPath("$.summary.executionEngineReady").value(false));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].runMode").value("BACKTEST"))
                .andExpect(jsonPath("$.content[0].status").value("QUEUED"));
    }

    @Test
    void requestRun_shouldUseLinkedPortfolioBalanceWhenInitialCapitalMissing() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Forward Ready")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(15)
                .status(StrategyBot.Status.READY)
                .build());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("runMode", "FORWARD_TEST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runMode").value("FORWARD_TEST"))
                .andExpect(jsonPath("$.effectiveInitialCapital").value(120000));
    }

    @Test
    void requestRun_shouldRejectDraftBot() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .name("Still Draft")
                .market("CRYPTO")
                .symbol("SOLUSDT")
                .timeframe("15M")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("15"))
                .cooldownMinutes(5)
                .status(StrategyBot.Status.DRAFT)
                .build());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("runMode", "BACKTEST"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_not_ready"));
    }
}
