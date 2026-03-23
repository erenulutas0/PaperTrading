package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.domain.StrategyBotRunEquityPoint;
import com.finance.core.domain.StrategyBotRunFill;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @Autowired
    private StrategyBotRunFillRepository strategyBotRunFillRepository;

    @Autowired
    private StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;
    private Portfolio linkedPortfolio;

    @BeforeEach
    void setUp() {
        strategyBotRunEquityPointRepository.deleteAll();
        strategyBotRunFillRepository.deleteAll();
        strategyBotRunRepository.deleteAll();
        strategyBotRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();
        AppUser savedUser = userRepository.save(AppUser.builder()
                .username("strategy-owner")
                .email("strategy-owner@example.com")
                .password("hashed")
                .displayName("Strategy Owner")
                .trustScore(61.5)
                .build());
        userId = savedUser.getId();
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
    void listStrategyBots_shouldRejectMissingUser() throws Exception {
        mockMvc.perform(get("/api/v1/strategy-bots")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"));
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
    void createStrategyBot_shouldRejectMissingUser() throws Exception {
        Map<String, Object> request = Map.of(
                "name", "Ghost Bot",
                "market", "CRYPTO",
                "symbol", "SOLUSDT",
                "timeframe", "15M",
                "entryRules", Map.of(),
                "exitRules", Map.of(),
                "maxPositionSizePercent", 15);

        mockMvc.perform(post("/api/v1/strategy-bots")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void createStrategyBot_shouldRejectMissingPayloadAndRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/strategy-bots")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("strategy_bot_payload_required"));

        mockMvc.perform(post("/api/v1/strategy-bots")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "market", "CRYPTO",
                                "symbol", "BTCUSDT",
                                "timeframe", "1H",
                                "maxPositionSizePercent", 20))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("strategy_bot_name_required"));
    }

    @Test
    void createOrUpdateStrategyBot_shouldRejectInvalidRiskInputs() throws Exception {
        mockMvc.perform(post("/api/v1/strategy-bots")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Risk Bot",
                                "market", "CRYPTO",
                                "symbol", "BTCUSDT",
                                "timeframe", "1H",
                                "maxPositionSizePercent", 120))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("strategy_bot_max_position_size_invalid"));

        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Cooldown Bot")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(10)
                .status(StrategyBot.Status.DRAFT)
                .build());

        mockMvc.perform(put("/api/v1/strategy-bots/" + bot.getId())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("cooldownMinutes", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("strategy_bot_cooldown_invalid"));
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

    @Test
    void requestStrategyBotRun_shouldRejectInvalidRunInputs() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Runner")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(15)
                .status(StrategyBot.Status.READY)
                .build());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "runMode", "PAPER_LIVE",
                                "initialCapital", 100000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_run_mode"));

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fromDate", "2026-03-10",
                                "toDate", "2026-03-09"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_date_range_invalid"));

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "initialCapital", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("strategy_bot_initial_capital_invalid"));
    }

    @Test
    void getStrategyBotAnalytics_shouldAggregateRunMetricsAndScorecards() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Analytics Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 10.0,
                          "netPnl": 10000.0,
                          "maxDrawdownPercent": 4.0,
                          "winRate": 60.0,
                          "tradeCount": 5,
                          "profitFactor": 1.8,
                          "expectancyPerTrade": 2000.0,
                          "timeInMarketPercent": 40.0,
                          "linkedPortfolioAligned": false,
                          "entryReasonCounts": {"price_above_ma_3": 2},
                          "exitReasonCounts": {"take_profit_hit": 1}
                        }
                        """)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(3))
                .completedAt(LocalDateTime.now().minusDays(2))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": -6.0,
                          "netPnl": -6000.0,
                          "maxDrawdownPercent": 9.0,
                          "winRate": 20.0,
                          "tradeCount": 3,
                          "profitFactor": 0.5,
                          "expectancyPerTrade": -2000.0,
                          "timeInMarketPercent": 22.0,
                          "linkedPortfolioAligned": true,
                          "entryReasonCounts": {"price_above_ma_3": 1},
                          "exitReasonCounts": {"stop_loss_hit": 2}
                        }
                        """)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusMinutes(5))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 1.5,
                          "netPnl": 1500.0,
                          "tradeCount": 1,
                          "timeInMarketPercent": 12.0,
                          "lastEvaluatedOpenTime": 1711111111,
                          "entryReasonCounts": {"price_above_ma_3": 1},
                          "exitReasonCounts": {}
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/analytics")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyBotId").value(bot.getId().toString()))
                .andExpect(jsonPath("$.totalRuns").value(3))
                .andExpect(jsonPath("$.completedRuns").value(2))
                .andExpect(jsonPath("$.runningRuns").value(1))
                .andExpect(jsonPath("$.backtestRuns").value(2))
                .andExpect(jsonPath("$.forwardTestRuns").value(1))
                .andExpect(jsonPath("$.positiveCompletedRuns").value(1))
                .andExpect(jsonPath("$.negativeCompletedRuns").value(1))
                .andExpect(jsonPath("$.avgReturnPercent").value(2.0))
                .andExpect(jsonPath("$.avgTradeCount").value(4.0))
                .andExpect(jsonPath("$.entryDriverTotals.price_above_ma_3").value(4))
                .andExpect(jsonPath("$.exitDriverTotals.stop_loss_hit").value(2))
                .andExpect(jsonPath("$.bestRun.returnPercent").value(10.0))
                .andExpect(jsonPath("$.worstRun.returnPercent").value(-6.0))
                .andExpect(jsonPath("$.activeForwardRun.runMode").value("FORWARD_TEST"))
                .andExpect(jsonPath("$.recentScorecards", hasSize(3)));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/analytics")
                        .header("X-User-Id", userId.toString())
                        .param("runMode", "BACKTEST")
                        .param("lookbackDays", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRuns").value(1))
                .andExpect(jsonPath("$.completedRuns").value(1))
                .andExpect(jsonPath("$.backtestRuns").value(1))
                .andExpect(jsonPath("$.forwardTestRuns").value(0))
                .andExpect(jsonPath("$.avgReturnPercent").value(10.0))
                .andExpect(jsonPath("$.recentScorecards", hasSize(1)));
    }

    @Test
    void exportStrategyBotAnalytics_shouldReturnCsvAndJsonDownloads() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Analytics Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 8.5,
                          "netPnl": 8500.0,
                          "maxDrawdownPercent": 3.2,
                          "winRate": 75.0,
                          "tradeCount": 4,
                          "profitFactor": 2.1,
                          "expectancyPerTrade": 2125.0,
                          "timeInMarketPercent": 38.0,
                          "linkedPortfolioAligned": true,
                          "entryReasonCounts": {"price_above_ma_3": 2},
                          "exitReasonCounts": {"take_profit_hit": 1}
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/analytics/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("context,name,Analytics Bot")))
                .andExpect(content().string(containsString("recentScorecard,recent")));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/analytics/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Analytics Bot"))
                .andExpect(jsonPath("$.analytics.totalRuns").value(1))
                .andExpect(jsonPath("$.analytics.recentScorecards", hasSize(1)));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/analytics/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "json")
                        .param("runMode", "FORWARD_TEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.runModeScope").value("FORWARD_TEST"))
                .andExpect(jsonPath("$.lookbackDays").value(30))
                .andExpect(jsonPath("$.analytics.totalRuns").value(0))
                .andExpect(jsonPath("$.analytics.recentScorecards", hasSize(0)));
    }

    @Test
    void getStrategyBotBoard_shouldReturnSortedPagedEntries() throws Exception {
        StrategyBot strongerBot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Momentum Prime")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build());
        StrategyBot weakerBot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Mean Revert")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(strongerBot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusHours(4))
                .completedAt(LocalDateTime.now().minusHours(3))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 11.0,
                          "netPnl": 11000.0,
                          "maxDrawdownPercent": 3.2,
                          "winRate": 75.0,
                          "tradeCount": 4,
                          "profitFactor": 2.3
                        }
                        """)
                .build());
        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(strongerBot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "maxDrawdownPercent": 2.0,
                          "winRate": 60.0,
                          "tradeCount": 2,
                          "profitFactor": 1.4
                        }
                        """)
                .build());
        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(weakerBot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": -2.5,
                          "netPnl": -2500.0,
                          "maxDrawdownPercent": 6.5,
                          "winRate": 40.0,
                          "tradeCount": 5,
                          "profitFactor": 0.9
                        }
                        """)
                .build());
        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(weakerBot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(45))
                .completedAt(LocalDateTime.now().minusDays(44))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 9.0,
                          "netPnl": 9000.0,
                          "maxDrawdownPercent": 4.0,
                          "winRate": 55.0,
                          "tradeCount": 3,
                          "profitFactor": 1.7
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", userId.toString())
                        .param("sortBy", "AVG_RETURN")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].strategyBotId").value(strongerBot.getId().toString()))
                .andExpect(jsonPath("$.content[0].avgReturnPercent").value(7.5))
                .andExpect(jsonPath("$.content[1].strategyBotId").value(weakerBot.getId().toString()))
                .andExpect(jsonPath("$.content[1].avgReturnPercent").value(3.25));

        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", userId.toString())
                        .param("sortBy", "AVG_RETURN")
                        .param("direction", "DESC")
                        .param("runMode", "FORWARD_TEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].strategyBotId").value(strongerBot.getId().toString()))
                .andExpect(jsonPath("$.content[0].avgReturnPercent").value(4.0))
                .andExpect(jsonPath("$.content[0].totalRuns").value(1))
                .andExpect(jsonPath("$.content[1].strategyBotId").value(weakerBot.getId().toString()))
                .andExpect(jsonPath("$.content[1].avgReturnPercent").value(nullValue()))
                .andExpect(jsonPath("$.content[1].totalRuns").value(0));

        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", userId.toString())
                        .param("sortBy", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_sort"));

        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", userId.toString())
                        .param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_direction"));

        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", userId.toString())
                        .param("runMode", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_run_mode"));

        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", userId.toString())
                        .param("lookbackDays", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_lookback"));
    }

    @Test
    void getStrategyBotBoard_shouldRejectMissingUser() throws Exception {
        mockMvc.perform(get("/api/v1/strategy-bots/board")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void exportStrategyBotBoard_shouldReturnScopedCsvAndJson() throws Exception {
        StrategyBot firstBot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Board Leader")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build());
        StrategyBot secondBot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Board Challenger")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.DRAFT)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(firstBot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(3))
                .completedAt(LocalDateTime.now().minusDays(2))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 4.5,
                          "netPnl": 4500.0,
                          "maxDrawdownPercent": 2.5,
                          "winRate": 62.0,
                          "tradeCount": 3,
                          "profitFactor": 1.5
                        }
                        """)
                .build());
        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(secondBot.getId())
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(40))
                .completedAt(LocalDateTime.now().minusDays(39))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 9.0,
                          "netPnl": 9000.0,
                          "maxDrawdownPercent": 5.0,
                          "winRate": 55.0,
                          "tradeCount": 2,
                          "profitFactor": 1.2
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/board/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "csv")
                        .param("runMode", "FORWARD_TEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("strategy-bot-board.csv")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("context,runModeScope,FORWARD_TEST")))
                .andExpect(content().string(containsString("context,lookbackDays,30")))
                .andExpect(content().string(containsString("boardEntry,Board Leader")))
                .andExpect(content().string(containsString("boardEntry,Board Challenger")));

        mockMvc.perform(get("/api/v1/strategy-bots/board/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "json")
                        .param("sortBy", "AVG_RETURN")
                        .param("direction", "DESC")
                        .param("runMode", "FORWARD_TEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("strategy-bot-board.json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sortBy").value("AVG_RETURN"))
                .andExpect(jsonPath("$.direction").value("DESC"))
                .andExpect(jsonPath("$.runModeScope").value("FORWARD_TEST"))
                .andExpect(jsonPath("$.lookbackDays").value(30))
                .andExpect(jsonPath("$.entryCount").value(2))
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].strategyBotId").value(firstBot.getId().toString()))
                .andExpect(jsonPath("$.entries[0].totalRuns").value(1))
                .andExpect(jsonPath("$.entries[1].strategyBotId").value(secondBot.getId().toString()))
                .andExpect(jsonPath("$.entries[1].totalRuns").value(0));

        mockMvc.perform(get("/api/v1/strategy-bots/board/export")
                        .header("X-User-Id", userId.toString())
                        .param("sortBy", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_sort"));

        mockMvc.perform(get("/api/v1/strategy-bots/board/export")
                        .header("X-User-Id", userId.toString())
                        .param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_direction"));
    }

    @Test
    void exportStrategyBotBoard_shouldRejectInvalidFormat() throws Exception {
        mockMvc.perform(get("/api/v1/strategy-bots/board/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_export_format"))
                .andExpect(jsonPath("$.message").value("Invalid strategy bot export format"));
    }

    @Test
    void discoverStrategyBots_shouldReturnPublicNonDraftBotsWithOwnerContext() throws Exception {
        AppUser publicOwner = userRepository.save(AppUser.builder()
                .username("aurora")
                .email("aurora@example.com")
                .password("hashed")
                .displayName("Aurora Signals")
                .avatarUrl("https://example.com/avatar.png")
                .trustScore(72.25)
                .build());
        UUID publicOwnerId = publicOwner.getId();

        Portfolio publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Aurora Public Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("150000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());
        Portfolio privatePortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Aurora Private Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("90000"))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());

        StrategyBot publicReadyBot = strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Aurora Breakout")
                .description("Public breakout engine")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build());
        strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Aurora Draft")
                .description("Should stay private")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("15"))
                .cooldownMinutes(30)
                .status(StrategyBot.Status.DRAFT)
                .build());
        strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(privatePortfolio.getId())
                .name("Aurora Private Bot")
                .description("Private basket only")
                .market("CRYPTO")
                .symbol("SOLUSDT")
                .timeframe("15M")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("15"))
                .cooldownMinutes(30)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(publicReadyBot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(1))
                .completedAt(LocalDateTime.now().minusHours(12))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 12.0,
                          "netPnl": 12000.0,
                          "maxDrawdownPercent": 4.0,
                          "winRate": 66.0,
                          "tradeCount": 6,
                          "profitFactor": 1.9
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/discover")
                        .param("q", "aurora")
                        .param("sortBy", "AVG_RETURN")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].strategyBotId").value(publicReadyBot.getId().toString()))
                .andExpect(jsonPath("$.content[0].name").value("Aurora Breakout"))
                .andExpect(jsonPath("$.content[0].description").value("Public breakout engine"))
                .andExpect(jsonPath("$.content[0].linkedPortfolioId").value(publicPortfolio.getId().toString()))
                .andExpect(jsonPath("$.content[0].linkedPortfolioName").value("Aurora Public Basket"))
                .andExpect(jsonPath("$.content[0].ownerId").value(publicOwnerId.toString()))
                .andExpect(jsonPath("$.content[0].ownerUsername").value("aurora"))
                .andExpect(jsonPath("$.content[0].ownerDisplayName").value("Aurora Signals"))
                .andExpect(jsonPath("$.content[0].ownerAvatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.content[0].ownerTrustScore").value(72.25))
                .andExpect(jsonPath("$.content[0].avgReturnPercent").value(12.0))
                .andExpect(jsonPath("$.content[0].totalRuns").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        mockMvc.perform(get("/api/v1/strategy-bots/discover")
                        .param("sortBy", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_sort"));

        mockMvc.perform(get("/api/v1/strategy-bots/discover")
                        .param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_direction"));
    }

    @Test
    void getPublicStrategyBotDetail_shouldReturnScopedPublicAnalyticsAndMetadata() throws Exception {
        AppUser publicOwner = userRepository.save(AppUser.builder()
                .username("detail-owner")
                .email("detail-owner@example.com")
                .password("hashed")
                .displayName("Detail Owner")
                .trustScore(68.4)
                .build());
        UUID publicOwnerId = publicOwner.getId();

        Portfolio publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Detail Public Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("175000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Detail Breakout Bot")
                .description("Public detail profile")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{\"all\":[\"price_above_ma_50\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("22.5"))
                .stopLossPercent(new BigDecimal("4.5"))
                .takeProfitPercent(new BigDecimal("12.0"))
                .cooldownMinutes(45)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(3))
                .completedAt(LocalDateTime.now().minusDays(2))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 7.0,
                          "netPnl": 7000.0,
                          "maxDrawdownPercent": 3.2,
                          "winRate": 65.0,
                          "tradeCount": 4,
                          "profitFactor": 1.7,
                          "entryReasonCounts": {"price_above_ma_50": 2},
                          "exitReasonCounts": {"take_profit_hit": 1}
                        }
                        """)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusHours(3))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 2.0,
                          "netPnl": 2000.0,
                          "tradeCount": 1,
                          "lastEvaluatedOpenTime": 1711111111
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId())
                        .param("runMode", "BACKTEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyBotId").value(bot.getId().toString()))
                .andExpect(jsonPath("$.name").value("Detail Breakout Bot"))
                .andExpect(jsonPath("$.description").value("Public detail profile"))
                .andExpect(jsonPath("$.botKind").value("RULE_BASED"))
                .andExpect(jsonPath("$.linkedPortfolioId").value(publicPortfolio.getId().toString()))
                .andExpect(jsonPath("$.linkedPortfolioName").value("Detail Public Basket"))
                .andExpect(jsonPath("$.ownerId").value(publicOwnerId.toString()))
                .andExpect(jsonPath("$.ownerUsername").value("detail-owner"))
                .andExpect(jsonPath("$.ownerDisplayName").value("Detail Owner"))
                .andExpect(jsonPath("$.ownerTrustScore").value(68.4))
                .andExpect(jsonPath("$.maxPositionSizePercent").value(22.5))
                .andExpect(jsonPath("$.stopLossPercent").value(4.5))
                .andExpect(jsonPath("$.takeProfitPercent").value(12.0))
                .andExpect(jsonPath("$.cooldownMinutes").value(45))
                .andExpect(jsonPath("$.entryRules.all", hasSize(1)))
                .andExpect(jsonPath("$.analytics.totalRuns").value(1))
                .andExpect(jsonPath("$.analytics.backtestRuns").value(1))
                .andExpect(jsonPath("$.analytics.forwardTestRuns").value(0))
                .andExpect(jsonPath("$.analytics.avgReturnPercent").value(7.0))
                .andExpect(jsonPath("$.analytics.recentScorecards", hasSize(1)))
                .andExpect(jsonPath("$.analytics.bestRun.returnPercent").value(7.0));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("strategy_bot_not_found"));
    }

    @Test
    void exportPublicStrategyBotBoard_shouldReturnScopedCsvAndJson() throws Exception {
        AppUser publicOwner = userRepository.save(AppUser.builder()
                .username("board-owner")
                .email("board-owner@example.com")
                .password("hashed")
                .displayName("Board Owner")
                .trustScore(70.0)
                .build());
        UUID publicOwnerId = publicOwner.getId();

        Portfolio publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Board Public Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("125000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Board Export Bot")
                .description("Exportable public board row")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("18"))
                .cooldownMinutes(30)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 6.5,
                          "netPnl": 6500.0,
                          "maxDrawdownPercent": 2.4,
                          "winRate": 61.0,
                          "tradeCount": 3,
                          "profitFactor": 1.6
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/discover/export")
                        .param("format", "csv")
                        .param("q", "board")
                        .param("runMode", "BACKTEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("public-strategy-bot-board.csv")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("context,visibilityScope,PUBLIC")))
                .andExpect(content().string(containsString("context,q,board")))
                .andExpect(content().string(containsString("boardEntry,Board Export Bot")));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/export")
                        .param("format", "json")
                        .param("sortBy", "AVG_RETURN")
                        .param("direction", "DESC")
                        .param("runMode", "BACKTEST")
                        .param("lookbackDays", "30")
                        .param("q", "board"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("public-strategy-bot-board.json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.visibilityScope").value("PUBLIC"))
                .andExpect(jsonPath("$.sortBy").value("AVG_RETURN"))
                .andExpect(jsonPath("$.runModeScope").value("BACKTEST"))
                .andExpect(jsonPath("$.lookbackDays").value(30))
                .andExpect(jsonPath("$.q").value("board"))
                .andExpect(jsonPath("$.entryCount").value(1))
                .andExpect(jsonPath("$.entries[0].strategyBotId").value(bot.getId().toString()));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/export")
                        .param("sortBy", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_sort"));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/export")
                        .param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_board_direction"));
    }

    @Test
    void exportPublicStrategyBotBoard_shouldRejectInvalidFormat() throws Exception {
        mockMvc.perform(get("/api/v1/strategy-bots/discover/export")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_export_format"))
                .andExpect(jsonPath("$.message").value("Invalid strategy bot export format"));
    }

    @Test
    void exportPublicStrategyBotDetail_shouldReturnScopedCsvAndJson() throws Exception {
        AppUser publicOwner = userRepository.save(AppUser.builder()
                .username("detail-export-owner")
                .email("detail-export-owner@example.com")
                .password("hashed")
                .displayName("Detail Export Owner")
                .trustScore(74.5)
                .build());
        UUID publicOwnerId = publicOwner.getId();

        Portfolio publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Detail Export Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("210000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Detail Export Bot")
                .description("Public detail export profile")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4H")
                .entryRules("{\"all\":[\"price_above_ma_20\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("24"))
                .stopLossPercent(new BigDecimal("3.5"))
                .takeProfitPercent(new BigDecimal("11.0"))
                .cooldownMinutes(50)
                .status(StrategyBot.Status.READY)
                .build());

        strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .compiledEntryRules("{}")
                .compiledExitRules("{}")
                .summary("""
                        {
                          "returnPercent": 5.0,
                          "netPnl": 5000.0,
                          "maxDrawdownPercent": 2.1,
                          "winRate": 58.0,
                          "tradeCount": 2,
                          "profitFactor": 1.4,
                          "entryReasonCounts": {"price_above_ma_20": 1},
                          "exitReasonCounts": {"take_profit_hit": 1}
                        }
                        """)
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId() + "/export")
                        .param("format", "csv")
                        .param("runMode", "BACKTEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("public-strategy-bot-" + bot.getId() + ".csv")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("context,name,Detail Export Bot")))
                .andExpect(content().string(containsString("rules,entryRules")))
                .andExpect(content().string(containsString("recentScorecard,recent")));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId() + "/export")
                        .param("format", "json")
                        .param("runMode", "BACKTEST")
                        .param("lookbackDays", "30"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("public-strategy-bot-" + bot.getId() + ".json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Detail Export Bot"))
                .andExpect(jsonPath("$.ownerDisplayName").value("Detail Export Owner"))
                .andExpect(jsonPath("$.runModeScope").value("BACKTEST"))
                .andExpect(jsonPath("$.lookbackDays").value(30))
                .andExpect(jsonPath("$.analytics.totalRuns").value(1))
                .andExpect(jsonPath("$.entryRules.all", hasSize(1)));
    }

    @Test
    void getPublicStrategyBotRunDetail_shouldReturnRunRowsAndSummary() throws Exception {
        AppUser publicOwner = userRepository.save(AppUser.builder()
                .username("run-owner")
                .email("run-owner@example.com")
                .password("hashed")
                .displayName("Run Owner")
                .trustScore(69.0)
                .build());
        UUID publicOwnerId = publicOwner.getId();

        Portfolio publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Run Public Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("190000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Run Drilldown Bot")
                .description("Public run inspection")
                .market("CRYPTO")
                .symbol("SOLUSDT")
                .timeframe("1H")
                .entryRules("{\"all\":[\"price_above_ma_20\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("16"))
                .cooldownMinutes(20)
                .status(StrategyBot.Status.READY)
                .build());

        StrategyBotRun run = strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(3))
                .startedAt(LocalDateTime.now().minusDays(3).plusMinutes(1))
                .completedAt(LocalDateTime.now().minusDays(2))
                .compiledEntryRules("{\"all\":[\"price_above_ma_20\"]}")
                .compiledExitRules("{\"any\":[\"take_profit_hit\"]}")
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 9.0,
                          "netPnl": 9000.0,
                          "maxDrawdownPercent": 2.8,
                          "winRate": 100.0,
                          "tradeCount": 1,
                          "profitFactor": 2.0
                        }
                        """)
                .build());

        strategyBotRunFillRepository.save(StrategyBotRunFill.builder()
                .strategyBotRunId(run.getId())
                .sequenceNo(1)
                .side("ENTRY")
                .openTime(1711000000000L)
                .price(new BigDecimal("135.25"))
                .quantity(new BigDecimal("4.25000000"))
                .realizedPnl(new BigDecimal("0.00"))
                .matchedRules("[\"price_above_ma_20\"]")
                .build());
        strategyBotRunEquityPointRepository.save(StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(run.getId())
                .sequenceNo(1)
                .openTime(1711000000000L)
                .closePrice(new BigDecimal("135.25"))
                .equity(new BigDecimal("100000.00"))
                .build());
        strategyBotRunEquityPointRepository.save(StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(run.getId())
                .sequenceNo(2)
                .openTime(1711003600000L)
                .closePrice(new BigDecimal("139.50"))
                .equity(new BigDecimal("109000.00"))
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId() + "/runs/" + run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategyBotId").value(bot.getId().toString()))
                .andExpect(jsonPath("$.runId").value(run.getId().toString()))
                .andExpect(jsonPath("$.botName").value("Run Drilldown Bot"))
                .andExpect(jsonPath("$.ownerDisplayName").value("Run Owner"))
                .andExpect(jsonPath("$.linkedPortfolioName").value("Run Public Basket"))
                .andExpect(jsonPath("$.runMode").value("BACKTEST"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.returnPercent").value(9.0))
                .andExpect(jsonPath("$.summary.tradeCount").value(1))
                .andExpect(jsonPath("$.fills", hasSize(1)))
                .andExpect(jsonPath("$.fills[0].side").value("ENTRY"))
                .andExpect(jsonPath("$.fills[0].matchedRules[0]").value("price_above_ma_20"))
                .andExpect(jsonPath("$.equityCurve", hasSize(2)))
                .andExpect(jsonPath("$.equityCurve[1].equity").value(109000.00));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId() + "/runs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_not_found"));
    }

    @Test
    void exportPublicStrategyBotRunDetail_shouldReturnCsvAndJson() throws Exception {
        AppUser publicOwner = userRepository.save(AppUser.builder()
                .username("run-export-owner")
                .email("run-export-owner@example.com")
                .password("hashed")
                .displayName("Run Export Owner")
                .trustScore(71.5)
                .build());
        UUID publicOwnerId = publicOwner.getId();

        Portfolio publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Run Export Basket")
                .ownerId(publicOwnerId.toString())
                .balance(new BigDecimal("160000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .name("Run Export Bot")
                .description("Public run export profile")
                .market("CRYPTO")
                .symbol("BNBUSDT")
                .timeframe("4H")
                .entryRules("{\"all\":[\"price_above_ma_20\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("14"))
                .cooldownMinutes(15)
                .status(StrategyBot.Status.READY)
                .build());

        StrategyBotRun run = strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(publicOwnerId)
                .linkedPortfolioId(publicPortfolio.getId())
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedInitialCapital(new BigDecimal("100000"))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .compiledEntryRules("{\"all\":[\"price_above_ma_20\"]}")
                .compiledExitRules("{\"any\":[\"take_profit_hit\"]}")
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "tradeCount": 1,
                          "entryReasonCounts": {"price_above_ma_20": 1}
                        }
                        """)
                .build());

        strategyBotRunFillRepository.save(StrategyBotRunFill.builder()
                .strategyBotRunId(run.getId())
                .sequenceNo(1)
                .side("ENTRY")
                .openTime(1712000000000L)
                .price(new BigDecimal("580.00"))
                .quantity(new BigDecimal("2.00000000"))
                .realizedPnl(new BigDecimal("0.00"))
                .matchedRules("[\"price_above_ma_20\"]")
                .build());
        strategyBotRunEquityPointRepository.save(StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(run.getId())
                .sequenceNo(1)
                .openTime(1712000000000L)
                .closePrice(new BigDecimal("580.00"))
                .equity(new BigDecimal("100000.00"))
                .build());

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId() + "/runs/" + run.getId() + "/export")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("public-strategy-bot-run-" + run.getId() + ".csv")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("context,botName,Run Export Bot")))
                .andExpect(content().string(containsString("rules,compiledEntryRules")))
                .andExpect(content().string(containsString("fill,1")));

        mockMvc.perform(get("/api/v1/strategy-bots/discover/" + bot.getId() + "/runs/" + run.getId() + "/export")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("public-strategy-bot-run-" + run.getId() + ".json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.botName").value("Run Export Bot"))
                .andExpect(jsonPath("$.run.runId").value(run.getId().toString()))
                .andExpect(jsonPath("$.run.summary.returnPercent").value(4.0))
                .andExpect(jsonPath("$.run.fills", hasSize(1)))
                .andExpect(jsonPath("$.run.equityCurve", hasSize(1)));
    }
}
