package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.service.MarketDataFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
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
    @MockitoBean
    private MarketDataFacadeService marketDataFacadeService;

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
                .stopLossPercent(new BigDecimal("3.5"))
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
                .andExpect(jsonPath("$.summary.executionEngineReady").value(true))
                .andExpect(jsonPath("$.summary.supportedEntryRuleCount").value(1))
                .andExpect(jsonPath("$.summary.supportedExitRuleCount").value(1));

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

    @Test
    void requestRun_shouldSurfaceUnsupportedRulesInQueuedSummary() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .name("Unsupported Rule Bot")
                .market("CRYPTO")
                .symbol("ADAUSDT")
                .timeframe("30M")
                .entryRules("{\"all\":[\"macd_cross\"]}")
                .exitRules("{\"any\":[\"stop_loss_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("12"))
                .cooldownMinutes(10)
                .status(StrategyBot.Status.READY)
                .build());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("runMode", "BACKTEST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.executionEngineReady").value(false))
                .andExpect(jsonPath("$.summary.unsupportedRules", hasSize(2)))
                .andExpect(jsonPath("$.summary.unsupportedRules[0]").value("macd_cross"));
    }

    @Test
    void executeRun_shouldPersistAndExposeFillAndEquityRows() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Execution Ready")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1000000)
                .status(StrategyBot.Status.READY)
                .build());

        when(marketDataFacadeService.getCandles(MarketType.CRYPTO, "BTCUSDT", "ALL", "1h", null, 500))
                .thenReturn(risingCandles());

        String runResponse = mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("runMode", "BACKTEST"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = objectMapper.readTree(runResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/execute")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.fillCount").value(2))
                .andExpect(jsonPath("$.summary.avgHoldHours").exists())
                .andExpect(jsonPath("$.summary.exitReasonCounts.take_profit_hit").value(1));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/fills")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].side").value("ENTRY"))
                .andExpect(jsonPath("$.content[1].side").value("EXIT"));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/equity-curve")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(risingCandles().size())))
                .andExpect(jsonPath("$.content[0].equity").exists());
    }

    @Test
    void executeRun_shouldStartForwardTestAndRefreshIt() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Forward Execution")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1000000)
                .status(StrategyBot.Status.READY)
                .build());

        when(marketDataFacadeService.getCandles(MarketType.CRYPTO, "BTCUSDT", "ALL", "1h", null, 500))
                .thenReturn(risingCandles());

        String runResponse = mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("runMode", "FORWARD_TEST"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = objectMapper.readTree(runResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/execute")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.summary.phase").value("running"))
                .andExpect(jsonPath("$.summary.lastEvaluatedOpenTime").exists());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/refresh")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.summary.phase").value("running"));
    }

    @Test
    void refreshRun_shouldCompleteForwardTestWhenToDateIsReached() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Forward Completion")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1000000)
                .status(StrategyBot.Status.READY)
                .build());

        when(marketDataFacadeService.getCandles(MarketType.CRYPTO, "BTCUSDT", "ALL", "1h", null, 500))
                .thenReturn(risingCandles());

        String runResponse = mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runMode", "FORWARD_TEST",
                                "fromDate", "2026-01-01",
                                "toDate", "2026-01-23"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = objectMapper.readTree(runResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/execute")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.phase").value("completed"));
    }

    private List<MarketCandleResponse> risingCandles() {
        return List.of(
                candle(1, 100, 101, 99, 100, 1000),
                candle(2, 100, 102, 99, 101, 1100),
                candle(3, 101, 103, 100, 102, 1200),
                candle(4, 102, 104, 101, 103, 1300),
                candle(5, 103, 105, 102, 104, 1400),
                candle(6, 104, 106, 103, 105, 1500),
                candle(7, 105, 107, 104, 106, 1600),
                candle(8, 106, 108, 105, 107, 1700),
                candle(9, 107, 109, 106, 108, 1800),
                candle(10, 108, 110, 107, 109, 1900),
                candle(11, 109, 111, 108, 110, 2000),
                candle(12, 110, 112, 109, 111, 2100),
                candle(13, 111, 113, 110, 112, 2200),
                candle(14, 112, 114, 111, 113, 2300),
                candle(15, 113, 115, 112, 114, 2400),
                candle(16, 114, 116, 113, 115, 2500),
                candle(17, 115, 117, 114, 116, 2600),
                candle(18, 116, 118, 115, 117, 2700),
                candle(19, 117, 119, 116, 118, 2800),
                candle(20, 118, 120, 117, 119, 2900),
                candle(21, 119, 121, 118, 120, 3000),
                candle(22, 120, 123, 119, 122.5, 3200));
    }

    private MarketCandleResponse candle(long openTime, double open, double high, double low, double close, double volume) {
        long baseEpochMillis = LocalDateTime.of(2026, 1, 1, 0, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        return MarketCandleResponse.builder()
                .openTime(baseEpochMillis + (openTime * 86_400_000L))
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .build();
    }
}
