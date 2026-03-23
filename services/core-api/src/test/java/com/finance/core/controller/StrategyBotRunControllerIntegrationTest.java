package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.domain.StrategyBotRunEquityPoint;
import com.finance.core.repository.StrategyBotRunEventRepository;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    private PortfolioItemRepository portfolioItemRepository;

    @Autowired
    private StrategyBotRunRepository strategyBotRunRepository;

    @Autowired
    private StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;

    @Autowired
    private StrategyBotRunEventRepository strategyBotRunEventRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private MarketDataFacadeService marketDataFacadeService;

    private UUID userId;
    private Portfolio linkedPortfolio;

    @BeforeEach
    void setUp() {
        strategyBotRunEventRepository.deleteAll();
        strategyBotRunEquityPointRepository.deleteAll();
        strategyBotRunRepository.deleteAll();
        strategyBotRepository.deleteAll();
        portfolioItemRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();
        AppUser savedUser = userRepository.save(AppUser.builder()
                .username("strategy-run-owner")
                .email("strategy-run-owner@example.com")
                .password("hashed")
                .displayName("Strategy Run Owner")
                .trustScore(63.0)
                .build());
        userId = savedUser.getId();
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
    void listRuns_shouldRejectInvalidPage() throws Exception {
        StrategyBot bot = saveBot(
                "Paged Runs",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs")
                        .header("X-User-Id", userId.toString())
                        .header("X-Request-Id", "strategy-bot-runs-page-err-1")
                        .param("page", "later"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "strategy-bot-runs-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_runs_page"))
                .andExpect(jsonPath("$.message").value("Invalid strategy bot runs page"))
                .andExpect(jsonPath("$.requestId").value("strategy-bot-runs-page-err-1"));
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
                .andExpect(jsonPath("$.summary.exitReasonCounts.take_profit_hit").value(1))
                .andExpect(jsonPath("$.summary.linkedPortfolioId").value(linkedPortfolio.getId().toString()))
                .andExpect(jsonPath("$.summary.linkedPortfolioBalance").value(120000))
                .andExpect(jsonPath("$.summary.linkedPortfolioAligned").value(false));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/fills")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].side").value("ENTRY"))
                .andExpect(jsonPath("$.content[1].side").value("EXIT"));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/events")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(risingCandles().size())))
                .andExpect(jsonPath("$.content[*].action", hasItem("ENTERED")))
                .andExpect(jsonPath("$.content[*].action", hasItem("EXITED")))
                .andExpect(jsonPath("$.content[0].details.positionOpen").exists());

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/equity-curve")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(risingCandles().size())))
                .andExpect(jsonPath("$.content[0].equity").exists());

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/reconciliation-plan")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedPortfolioId").value(linkedPortfolio.getId().toString()))
                .andExpect(jsonPath("$.targetCashBalance").exists())
                .andExpect(jsonPath("$.cashAligned").value(false))
                .andExpect(jsonPath("$.portfolioAligned").value(false));

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/apply-reconciliation")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashAligned").value(true))
                .andExpect(jsonPath("$.quantityAligned").value(true))
                .andExpect(jsonPath("$.portfolioAligned").value(true));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/reconciliation-plan")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolioAligned").value(true));

        mockMvc.perform(get("/api/v1/portfolios/" + linkedPortfolio.getId() + "/history")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("CASH SYNC (BOT)"))
                .andExpect(jsonPath("$.content[0].symbol").value("BTCUSDT"));
    }

    @Test
    void listRunFills_shouldRejectInvalidSize() throws Exception {
        StrategyBot bot = saveBot(
                "Fill Paging",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED, "{}");

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/fills")
                        .header("X-User-Id", userId.toString())
                        .header("X-Request-Id", "strategy-bot-run-fills-page-err-1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "strategy-bot-run-fills-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_run_fills_size"))
                .andExpect(jsonPath("$.message").value("Invalid strategy bot run fills size"))
                .andExpect(jsonPath("$.requestId").value("strategy-bot-run-fills-page-err-1"));
    }

    @Test
    void listRunEvents_shouldRejectInvalidPage() throws Exception {
        StrategyBot bot = saveBot(
                "Event Paging",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED, "{}");

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/events")
                        .header("X-User-Id", userId.toString())
                        .header("X-Request-Id", "strategy-bot-run-events-page-err-1")
                        .param("page", "later"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "strategy-bot-run-events-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_run_events_page"))
                .andExpect(jsonPath("$.message").value("Invalid strategy bot run events page"))
                .andExpect(jsonPath("$.requestId").value("strategy-bot-run-events-page-err-1"));
    }

    @Test
    void listRunEquityCurve_shouldRejectInvalidSize() throws Exception {
        StrategyBot bot = saveBot(
                "Equity Paging",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED, "{}");

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/equity-curve")
                        .header("X-User-Id", userId.toString())
                        .header("X-Request-Id", "strategy-bot-run-equity-page-err-1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "strategy-bot-run-equity-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_strategy_bot_run_equity_curve_size"))
                .andExpect(jsonPath("$.message").value("Invalid strategy bot run equity curve size"))
                .andExpect(jsonPath("$.requestId").value("strategy-bot-run-equity-page-err-1"));
    }

    @Test
    void exportRun_shouldReturnCsvAndJsonDownloads() throws Exception {
        StrategyBot bot = strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolio.getId())
                .name("Run Export Bot")
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
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("context,name,Run Export Bot")))
                .andExpect(content().string(containsString("event,ENTRY,ENTERED")))
                .andExpect(content().string(containsString("summary,tradeCount,1")))
                .andExpect(content().string(containsString("reconciliation,targetCashBalance")));

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/export")
                        .header("X-User-Id", userId.toString())
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".json")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Run Export Bot"))
                .andExpect(jsonPath("$.run.id").value(runId))
                .andExpect(jsonPath("$.events", hasSize(risingCandles().size())))
                .andExpect(jsonPath("$.events[0].phase").exists())
                .andExpect(jsonPath("$.fills", hasSize(2)))
                .andExpect(jsonPath("$.equityCurve", hasSize(risingCandles().size())))
                .andExpect(jsonPath("$.reconciliationPlan.linkedPortfolioId").value(linkedPortfolio.getId().toString()));
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
    void executeRun_shouldReturnConflictWhenMarketDataIsUnavailable() throws Exception {
        StrategyBot bot = saveBot(
                "Forward Unavailable",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);

        when(marketDataFacadeService.getCandles(MarketType.CRYPTO, "BTCUSDT", "ALL", "1h", null, 500))
                .thenThrow(new RuntimeException("I/O error on GET request for market candles"));

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_market_data_unavailable"))
                .andExpect(jsonPath("$.message").value("Strategy bot market data unavailable"));
    }

    @Test
    void executeRun_shouldReturnConflictWhenRunIsNotQueued() throws Exception {
        StrategyBot bot = saveBot(
                "Already Complete",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED, "{}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/execute")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_not_queued"));
    }

    @Test
    void cancelRun_shouldCancelQueuedRun() throws Exception {
        StrategyBot bot = saveBot(
                "Queued Cancel",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.QUEUED,
                "{\"phase\":\"queued\",\"executionEngineReady\":true}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/cancel")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.summary.phase").value("cancelled"))
                .andExpect(jsonPath("$.summary.previousStatus").value("QUEUED"))
                .andExpect(jsonPath("$.summary.executionEngineReady").value(true))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void cancelRun_shouldCancelRunningForwardTestRun() throws Exception {
        StrategyBot bot = saveBot(
                "Running Cancel",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);

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
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + runId + "/cancel")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.summary.phase").value("cancelled"))
                .andExpect(jsonPath("$.summary.previousStatus").value("RUNNING"))
                .andExpect(jsonPath("$.summary.lastEvaluatedOpenTime").exists())
                .andExpect(jsonPath("$.startedAt").exists())
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void cancelRun_shouldReturnConflictWhenRunIsNotCancellable() throws Exception {
        StrategyBot bot = saveBot(
                "Completed Cancel",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED, "{\"phase\":\"completed\"}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/cancel")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_not_cancellable"));
    }

    @Test
    void executeRun_shouldReturnConflictWhenExecutionEngineIsNotReady() throws Exception {
        StrategyBot bot = saveBot(
                "Unsupported Rule Execution",
                "BTCUSDT",
                "{\"all\":[\"macd_cross\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.QUEUED, "{}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/execute")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_not_executable"));
    }

    @Test
    void refreshRun_shouldReturnConflictWhenRunModeIsBacktest() throws Exception {
        StrategyBot bot = saveBot(
                "Backtest Refresh",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.RUNNING, "{}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/refresh")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_refresh_mode_not_supported"));
    }

    @Test
    void refreshRun_shouldReturnConflictWhenForwardTestIsNotRunning() throws Exception {
        StrategyBot bot = saveBot(
                "Queued Forward Refresh",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.FORWARD_TEST, StrategyBotRun.Status.QUEUED, "{}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/refresh")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_forward_test_not_running"));
    }

    @Test
    void getRunReconciliation_shouldReturnConflictWhenBotHasNoLinkedPortfolio() throws Exception {
        StrategyBot bot = saveBot(
                "No Linked Portfolio",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                null,
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED, "{\"endingEquity\":100000.00}");

        mockMvc.perform(get("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/reconciliation-plan")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_linked_portfolio_required"));
    }

    @Test
    void applyRunReconciliation_shouldReturnConflictWhenRunIsNotReady() throws Exception {
        StrategyBot bot = saveBot(
                "Queued Reconciliation",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.QUEUED, "{\"endingEquity\":100000.00}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/apply-reconciliation")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_run_reconciliation_not_ready"));
    }

    @Test
    void applyRunReconciliation_shouldReturnConflictWhenManualCleanupIsRequired() throws Exception {
        StrategyBot bot = saveBot(
                "Manual Cleanup",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED,
                "{\"endingEquity\":100000.00,\"positionOpen\":false,\"openQuantity\":null,\"openEntryPrice\":null}");
        portfolioItemRepository.save(PortfolioItem.builder()
                .portfolio(linkedPortfolio)
                .symbol("ETHUSDT")
                .quantity(new BigDecimal("1.00000000"))
                .averagePrice(new BigDecimal("3000.00"))
                .side("LONG")
                .leverage(1)
                .build());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/apply-reconciliation")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_reconciliation_manual_cleanup_required"));
    }

    @Test
    void applyRunReconciliation_shouldReturnConflictWhenTargetQuantityIsInvalid() throws Exception {
        StrategyBot bot = saveBot(
                "Invalid Quantity",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED,
                "{\"endingEquity\":100000.00,\"positionOpen\":true,\"openQuantity\":0,\"openEntryPrice\":3200.00}");
        strategyBotRunEquityPointRepository.save(StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(run.getId())
                .sequenceNo(1)
                .openTime(1713000000000L)
                .closePrice(new BigDecimal("3300.00"))
                .equity(new BigDecimal("100000.00"))
                .build());

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/apply-reconciliation")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_reconciliation_target_invalid"));
    }

    @Test
    void applyRunReconciliation_shouldReturnConflictWhenTargetPriceIsUnavailable() throws Exception {
        StrategyBot bot = saveBot(
                "Invalid Target Price",
                "BTCUSDT",
                "{\"all\":[\"price_above_ma_3\"]}",
                "{\"any\":[\"take_profit_hit\"]}",
                linkedPortfolio.getId(),
                StrategyBot.Status.READY);
        StrategyBotRun run = saveRun(bot, StrategyBotRun.RunMode.BACKTEST, StrategyBotRun.Status.COMPLETED,
                "{\"endingEquity\":100000.00,\"positionOpen\":true,\"openQuantity\":1.50000000,\"openEntryPrice\":3200.00}");

        mockMvc.perform(post("/api/v1/strategy-bots/" + bot.getId() + "/runs/" + run.getId() + "/apply-reconciliation")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("strategy_bot_reconciliation_target_price_unavailable"));
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

    private StrategyBot saveBot(String name,
                                String symbol,
                                String entryRules,
                                String exitRules,
                                UUID linkedPortfolioId,
                                StrategyBot.Status status) {
        return strategyBotRepository.save(StrategyBot.builder()
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name(name)
                .market("CRYPTO")
                .symbol(symbol)
                .timeframe("1h")
                .entryRules(entryRules)
                .exitRules(exitRules)
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1000000)
                .status(status)
                .build());
    }

    private StrategyBotRun saveRun(StrategyBot bot,
                                   StrategyBotRun.RunMode runMode,
                                   StrategyBotRun.Status status,
                                   String summary) {
        return strategyBotRunRepository.save(StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .runMode(runMode)
                .status(status)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary(summary)
                .build());
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
