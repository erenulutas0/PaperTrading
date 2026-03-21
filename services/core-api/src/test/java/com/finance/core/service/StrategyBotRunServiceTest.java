package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.domain.StrategyBotRunEquityPoint;
import com.finance.core.dto.StrategyBotRunReconciliationResponse;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyBotRunServiceTest {

    @Mock
    private StrategyBotRunRepository strategyBotRunRepository;
    @Mock
    private StrategyBotService strategyBotService;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private PortfolioItemRepository portfolioItemRepository;
    @Mock
    private StrategyBotRunFillRepository strategyBotRunFillRepository;
    @Mock
    private StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;
    @Mock
    private MarketDataFacadeService marketDataFacadeService;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Spy
    private StrategyBotRuleEngineService strategyBotRuleEngineService = new StrategyBotRuleEngineService();

    @InjectMocks
    private StrategyBotRunService strategyBotRunService;

    @Test
    void executeRun_shouldCompleteBacktestAndPersistPerformanceSummary() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("BTC Backtest")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1_000_000)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.QUEUED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 1, 31))
                .summary("{}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(portfolioRepository.findById(any())).thenReturn(Optional.of(Portfolio.builder()
                .id(UUID.randomUUID())
                .name("Linked Paper")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .build()));
        when(marketDataFacadeService.getCandles(eq(MarketType.CRYPTO), eq("BTCUSDT"), eq("ALL"), eq("1h"), eq(null), eq(500)))
                .thenReturn(risingCandles());
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRunResponse response = strategyBotRunService.executeRun(botId, runId, userId);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getSummary().get("phase").asText()).isEqualTo("completed");
        assertThat(response.getSummary().get("executionEngineReady").asBoolean()).isTrue();
        assertThat(response.getSummary().get("tradeCount").asInt()).isEqualTo(1);
        assertThat(response.getSummary().get("winCount").asInt()).isEqualTo(1);
        assertThat(response.getSummary().get("fillCount").asInt()).isEqualTo(2);
        assertThat(response.getSummary().get("endingEquity").asDouble()).isGreaterThan(100000.0);
        assertThat(response.getSummary().get("avgWinPnl").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("avgLossPnl").isNull()).isTrue();
        assertThat(response.getSummary().get("profitFactor").isNull()).isTrue();
        assertThat(response.getSummary().get("expectancyPerTrade").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("bestTradePnl").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("worstTradePnl").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("avgHoldHours").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("timeInMarketPercent").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("avgExposurePercent").asDouble()).isGreaterThan(0.0);
        assertThat(response.getSummary().get("entryReasonCounts").get("price_above_ma_3").asInt()).isEqualTo(1);
        assertThat(response.getSummary().get("exitReasonCounts").get("take_profit_hit").asInt()).isEqualTo(1);
        assertThat(response.getSummary().get("linkedPortfolioBalance").asDouble()).isEqualTo(100000.0);
        assertThat(response.getSummary().get("linkedPortfolioReferenceEquity").asDouble()).isGreaterThan(100000.0);
        assertThat(response.getSummary().get("linkedPortfolioAligned").asBoolean()).isFalse();
        assertThat(response.getSummary().get("fills")).hasSize(2);
        assertThat(response.getSummary().get("equityCurve")).hasSize(risingCandles().size());
        verify(strategyBotRunFillRepository).saveAll(any());
        verify(strategyBotRunEquityPointRepository).saveAll(any());

        ArgumentCaptor<StrategyBotRun> captor = ArgumentCaptor.forClass(StrategyBotRun.class);
        verify(strategyBotRunRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StrategyBotRun.Status.COMPLETED);
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void getRunReconciliation_shouldDescribePortfolioDriftAgainstRunState() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("BTC Recon")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1_000_000)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary("{\"endingEquity\":101176.47,\"positionOpen\":false,\"openQuantity\":null,\"openEntryPrice\":null}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(portfolioRepository.findById(linkedPortfolioId)).thenReturn(Optional.of(Portfolio.builder()
                .id(linkedPortfolioId)
                .name("Linked Paper")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .build()));
        when(portfolioItemRepository.findByPortfolioId(linkedPortfolioId)).thenReturn(List.of());
        when(strategyBotRunEquityPointRepository.findFirstByStrategyBotRunIdOrderBySequenceNoDesc(runId))
                .thenReturn(Optional.of(StrategyBotRunEquityPoint.builder()
                        .strategyBotRunId(runId)
                        .sequenceNo(1)
                        .openTime(System.currentTimeMillis())
                        .closePrice(new BigDecimal("122.50"))
                        .equity(new BigDecimal("101176.47"))
                        .build()));

        StrategyBotRunReconciliationResponse response = strategyBotRunService.getRunReconciliation(botId, runId, userId);

        assertThat(response.getLinkedPortfolioId()).isEqualTo(linkedPortfolioId);
        assertThat(response.isTargetPositionOpen()).isFalse();
        assertThat(response.getTargetCashBalance()).isEqualByComparingTo("101176.47");
        assertThat(response.getCurrentCashBalance()).isEqualByComparingTo("100000.00");
        assertThat(response.getCashDelta()).isEqualByComparingTo("1176.47");
        assertThat(response.isPortfolioAligned()).isFalse();
    }

    @Test
    void applyRunReconciliation_shouldSyncLinkedPortfolioState() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("BTC Sync")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary("{\"endingEquity\":104500.00,\"positionOpen\":true,\"openQuantity\":12.50000000,\"openEntryPrice\":3200.00}")
                .build();

        Portfolio linkedPortfolio = Portfolio.builder()
                .id(linkedPortfolioId)
                .name("Linked Paper")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .build();
        PortfolioItem syncedItem = PortfolioItem.builder()
                .portfolio(linkedPortfolio)
                .symbol("BTCUSDT")
                .quantity(new BigDecimal("12.50000000"))
                .averagePrice(new BigDecimal("3200.00"))
                .side("LONG")
                .leverage(1)
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(portfolioRepository.findById(linkedPortfolioId)).thenReturn(Optional.of(linkedPortfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(portfolioItemRepository.findByPortfolioId(linkedPortfolioId)).thenReturn(List.of(), List.of(syncedItem));
        when(portfolioItemRepository.save(any(PortfolioItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(strategyBotRunEquityPointRepository.findFirstByStrategyBotRunIdOrderBySequenceNoDesc(runId))
                .thenReturn(Optional.of(StrategyBotRunEquityPoint.builder()
                        .strategyBotRunId(runId)
                        .sequenceNo(10)
                        .openTime(System.currentTimeMillis())
                        .closePrice(new BigDecimal("3400.00"))
                        .equity(new BigDecimal("104500.00"))
                        .build()));

        StrategyBotRunReconciliationResponse response = strategyBotRunService.applyRunReconciliation(botId, runId, userId);

        assertThat(response.isPortfolioAligned()).isTrue();
        assertThat(response.getCurrentCashBalance()).isEqualByComparingTo("62000.00");
        assertThat(response.getCurrentQuantity()).isEqualByComparingTo("12.50000000");
        verify(portfolioRepository).save(any(Portfolio.class));
        verify(portfolioItemRepository).save(any(PortfolioItem.class));
        verify(portfolioItemRepository, never()).delete(any(PortfolioItem.class));
        verify(auditLogService).record(eq(userId), eq(com.finance.core.domain.AuditActionType.STRATEGY_BOT_RUN_RECONCILED), eq(com.finance.core.domain.AuditResourceType.STRATEGY_BOT_RUN), eq(runId), any());
    }

    @Test
    void executeRun_shouldRejectUnsupportedRuleSet() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .name("Unsupported")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"macd_cross\"]}")
                .exitRules("{\"any\":[]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .cooldownMinutes(0)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.QUEUED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary("{}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> strategyBotRunService.executeRun(botId, runId, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not executable by current engine");
    }

    @Test
    void executeRun_shouldStartForwardTestAndPersistRunningSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("BTC Forward")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1_000_000)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.QUEUED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary("{}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(portfolioRepository.findById(any())).thenReturn(Optional.of(Portfolio.builder()
                .id(UUID.randomUUID())
                .name("Linked Paper")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .build()));
        when(marketDataFacadeService.getCandles(eq(MarketType.CRYPTO), eq("BTCUSDT"), eq("ALL"), eq("1h"), eq(null), eq(500)))
                .thenReturn(risingCandles());
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRunResponse response = strategyBotRunService.executeRun(botId, runId, userId);

        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getSummary().get("phase").asText()).isEqualTo("running");
        assertThat(response.getSummary().get("lastEvaluatedOpenTime").asLong()).isGreaterThan(0L);
        assertThat(response.getSummary().get("endingEquity").asDouble()).isGreaterThan(100000.0);
        verify(strategyBotRunFillRepository).saveAll(any());
        verify(strategyBotRunEquityPointRepository).saveAll(any());
    }

    @Test
    void refreshForwardTestRun_shouldCompleteWhenForwardWindowEnds() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("BTC Forward End")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .takeProfitPercent(new BigDecimal("2"))
                .cooldownMinutes(1_000_000)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 1, 23))
                .summary("{}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(portfolioRepository.findById(any())).thenReturn(Optional.of(Portfolio.builder()
                .id(UUID.randomUUID())
                .name("Linked Paper")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .build()));
        when(marketDataFacadeService.getCandles(eq(MarketType.CRYPTO), eq("BTCUSDT"), eq("ALL"), eq("1h"), eq(null), eq(500)))
                .thenReturn(risingCandles());
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRunResponse response = strategyBotRunService.refreshForwardTestRun(botId, runId, userId);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getSummary().get("phase").asText()).isEqualTo("completed");
        assertThat(response.getCompletedAt()).isNotNull();
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
