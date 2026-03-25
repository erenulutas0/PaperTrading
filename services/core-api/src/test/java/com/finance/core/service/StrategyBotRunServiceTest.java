package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.domain.StrategyBotRunEquityPoint;
import com.finance.core.domain.StrategyBotRunEvent;
import com.finance.core.domain.StrategyBotRunFill;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.dto.StrategyBotAnalyticsResponse;
import com.finance.core.dto.StrategyBotBoardEntryResponse;
import com.finance.core.dto.StrategyBotRunReconciliationResponse;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunEventRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyBotRunServiceTest {

    @Mock
    private StrategyBotRunRepository strategyBotRunRepository;
    @Mock
    private StrategyBotRepository strategyBotRepository;
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
    private StrategyBotRunEventRepository strategyBotRunEventRepository;
    @Mock
    private TradeActivityRepository tradeActivityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MarketDataFacadeService marketDataFacadeService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PerformanceAnalyticsService performanceAnalyticsService;
    @Mock
    private CacheService cacheService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Spy
    private StrategyBotRuleEngineService strategyBotRuleEngineService = new StrategyBotRuleEngineService();

    @InjectMocks
    private StrategyBotRunService strategyBotRunService;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        lenient().when(userRepository.existsById(any())).thenReturn(true);
    }

    @Test
    void getBotAnalytics_shouldReuseCachedPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .name("Cached Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("25"))
                .status(StrategyBot.Status.READY)
                .cooldownMinutes(0)
                .build();
        StrategyBotRun run = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(1))
                .summary("""
                        {
                          "returnPercent": 5.0,
                          "netPnl": 5000.0,
                          "tradeCount": 2
                        }
                        """)
                .build();
        StrategyBotAnalyticsResponse cached = StrategyBotAnalyticsResponse.builder()
                .strategyBotId(botId)
                .totalRuns(99)
                .completedRuns(99)
                .build();
        String cachedJson = objectMapper.writeValueAsString(cached);

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(cacheService.get(anyString(), eq(String.class)))
                .thenReturn(Optional.empty(), Optional.of(cachedJson));
        when(strategyBotRunRepository.findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(botId, userId))
                .thenReturn(List.of(run));

        StrategyBotAnalyticsResponse first = strategyBotRunService.getBotAnalytics(botId, userId, "ALL", null);
        StrategyBotAnalyticsResponse second = strategyBotRunService.getBotAnalytics(botId, userId, "ALL", null);

        assertThat(first.getTotalRuns()).isEqualTo(1);
        assertThat(second.getTotalRuns()).isEqualTo(99);
        verify(strategyBotRunRepository).findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(botId, userId);
        verify(cacheService).set(contains("strategy-bot:analytics:" + botId), any(String.class), any(Duration.class));
    }

    @Test
    void requestRun_shouldInvalidateBotReadCaches() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .name("Runner")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("25"))
                .status(StrategyBot.Status.READY)
                .cooldownMinutes(0)
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> {
            StrategyBotRun saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        strategyBotRunService.requestRun(botId, userId, new com.finance.core.dto.StrategyBotRunRequest());

        verify(cacheService).deletePattern("strategy-bot:analytics:" + botId + ":*");
        verify(cacheService).deletePattern("strategy-bot:public-detail:" + botId + ":*");
        verify(cacheService).deletePattern("strategy-bot:board:*");
        verify(cacheService).deletePattern("strategy-bot:discover:*");
    }

    @Test
    void getBotBoard_shouldReuseCachedPageSnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .name("Board Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("25"))
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBotRun run = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(1))
                .completedAt(LocalDateTime.now().minusMinutes(30))
                .summary("""
                        {
                          "returnPercent": 3.5,
                          "netPnl": 3500.0,
                          "tradeCount": 1
                        }
                        """)
                .build();
        StrategyBotBoardEntryResponse cachedEntry = StrategyBotBoardEntryResponse.builder()
                .strategyBotId(botId)
                .name("Cached Board Bot")
                .status("READY")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .totalRuns(42)
                .completedRuns(40)
                .build();
        String cachedJson = objectMapper.writeValueAsString(java.util.Map.of(
                "content", List.of(cachedEntry),
                "totalElements", 42));

        when(cacheService.get(anyString(), eq(String.class)))
                .thenReturn(Optional.empty(), Optional.of(cachedJson));
        when(strategyBotRepository.findOwnedBotIdsOrderByAvgReturnDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(botId), PageRequest.of(0, 10), 1));
        when(strategyBotRepository.findAllById(List.of(botId))).thenReturn(List.of(bot));
        when(strategyBotRunRepository.findBoardAggregatesByStrategyBotIdIn(List.of(botId), "ALL", false, java.time.LocalDateTime.of(1970, 1, 1, 0, 0)))
                .thenReturn(List.of());
        when(strategyBotRunRepository.findBestCompletedRunIdsByStrategyBotIdIn(List.of(botId), "ALL", false, java.time.LocalDateTime.of(1970, 1, 1, 0, 0)))
                .thenReturn(List.of());
        when(strategyBotRunRepository.findLatestCompletedRunIdsByStrategyBotIdIn(List.of(botId), "ALL", false, java.time.LocalDateTime.of(1970, 1, 1, 0, 0)))
                .thenReturn(List.of());
        when(strategyBotRunRepository.findActiveForwardRunIdsByStrategyBotIdIn(List.of(botId), "ALL", false, java.time.LocalDateTime.of(1970, 1, 1, 0, 0)))
                .thenReturn(List.of());
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(List.of(botId), userId))
                .thenReturn(List.of(run));

        Page<StrategyBotBoardEntryResponse> first = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "AVG_RETURN",
                "DESC",
                "ALL",
                null);
        Page<StrategyBotBoardEntryResponse> second = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "AVG_RETURN",
                "DESC",
                "ALL",
                null);

        assertThat(first.getContent()).hasSize(1);
        assertThat(first.getContent().get(0).getName()).isEqualTo("Board Bot");
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.getContent().get(0).getName()).isEqualTo("Cached Board Bot");
        verify(strategyBotRepository, times(1)).findOwnedBotIdsOrderByAvgReturnDesc(eq(userId), any(Pageable.class));
        verify(cacheService).set(contains("strategy-bot:board:owned:user:" + userId), any(String.class), any(Duration.class));
    }

    @Test
    void parseJson_whenStoredPayloadIsInvalid_shouldThrowIllegalStateException() {
        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(strategyBotRunService, "parseJson", "{broken"));

        assertThat(exception).hasMessage("Failed to parse strategy bot run payload");
    }

    @Test
    void writeSummary_whenSerializationFails_shouldThrowIllegalStateException() throws Exception {
        ObjectMapper brokenMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        org.mockito.Mockito.doThrow(new JsonProcessingException("boom") { })
                .when(brokenMapper)
                .writeValueAsString(any());
        ReflectionTestUtils.setField(strategyBotRunService, "objectMapper", brokenMapper);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(strategyBotRunService, "writeSummary", java.util.Map.of("phase", "run")));

        assertThat(exception).hasMessage("Failed to serialize strategy bot run summary");
    }

    @Test
    void writePrettyJsonExport_whenSerializationFails_shouldThrowIllegalStateException() throws Exception {
        ObjectMapper brokenMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        ObjectWriter brokenWriter = org.mockito.Mockito.mock(ObjectWriter.class);
        when(brokenMapper.copy()).thenReturn(brokenMapper);
        when(brokenMapper.findAndRegisterModules()).thenReturn(brokenMapper);
        when(brokenMapper.writerWithDefaultPrettyPrinter()).thenReturn(brokenWriter);
        when(brokenWriter.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") { });
        ReflectionTestUtils.setField(strategyBotRunService, "objectMapper", brokenMapper);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        strategyBotRunService,
                        "writePrettyJsonExport",
                        java.util.Map.of("phase", "run"),
                        "Failed to serialize strategy bot run export"));

        assertThat(exception).hasMessage("Failed to serialize strategy bot run export");
    }

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
        assertThat(response.getSummary().get("eventCount").asInt()).isEqualTo(risingCandles().size());
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
        verify(strategyBotRunEventRepository).saveAll(any());

        ArgumentCaptor<StrategyBotRun> captor = ArgumentCaptor.forClass(StrategyBotRun.class);
        verify(strategyBotRunRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StrategyBotRun.Status.COMPLETED);
        assertThat(captor.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void localeSensitiveNormalizers_shouldRemainStableUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            org.junit.jupiter.api.Assertions.assertEquals(
                    StrategyBotRun.RunMode.FORWARD_TEST,
                    ReflectionTestUtils.invokeMethod(strategyBotRunService, "resolveRunMode", "forward_test"));
            org.junit.jupiter.api.Assertions.assertEquals(
                    StrategyBotRun.RunMode.FORWARD_TEST,
                    ReflectionTestUtils.invokeMethod(strategyBotRunService, "normalizeBoardRunMode", "forward_test"));
            org.junit.jupiter.api.Assertions.assertEquals(
                    MarketType.BIST100,
                    ReflectionTestUtils.invokeMethod(strategyBotRunService, "resolveMarketType", "bist100"));
            org.junit.jupiter.api.Assertions.assertEquals(
                    3_600_000L,
                    (Long) ReflectionTestUtils.invokeMethod(strategyBotRunService, "resolveTimeframeMillis", "1H"));
            org.junit.jupiter.api.Assertions.assertEquals(
                    100.0,
                    (Double) ReflectionTestUtils.invokeMethod(strategyBotRunService, "baseSyntheticPrice", "bist100"),
                    0.0);
        } finally {
            Locale.setDefault(previous);
        }
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
        verify(tradeActivityRepository).save(any(com.finance.core.domain.TradeActivity.class));
        verify(performanceAnalyticsService).invalidatePortfolioAnalytics(linkedPortfolioId);
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
                .isInstanceOfSatisfying(ApiRequestException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("strategy_bot_run_not_executable");
                    assertThat(ex.getMessage()).contains("not executable by current engine");
                });
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
    void executeRun_shouldUseSyntheticCryptoCandlesWhenLocalFallbackIsEnabled() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("Offline Forward")
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
                .thenThrow(new RuntimeException("I/O error on GET request"));
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReflectionTestUtils.setField(strategyBotRunService, "syntheticCryptoCandlesEnabled", true);
        ReflectionTestUtils.setField(strategyBotRunService, "syntheticCryptoCandleCount", 96);

        StrategyBotRunResponse response = strategyBotRunService.executeRun(botId, runId, userId);

        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(response.getSummary().get("phase").asText()).isEqualTo("running");
        assertThat(response.getSummary().get("lastEvaluatedOpenTime").asLong()).isGreaterThan(0L);
        assertThat(response.getSummary().get("candleCount").asInt()).isGreaterThanOrEqualTo(24);
    }

    @Test
    void cancelRun_shouldMarkQueuedRunCancelledAndAuditIt() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .name("Cancelable Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{\"all\":[\"price_above_ma_3\"]}")
                .exitRules("{\"any\":[\"take_profit_hit\"]}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .cooldownMinutes(30)
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
                .summary("{\"phase\":\"queued\",\"executionEngineReady\":true}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRunResponse response = strategyBotRunService.cancelRun(botId, runId, userId);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getSummary().get("phase").asText()).isEqualTo("cancelled");
        assertThat(response.getSummary().get("status").asText()).isEqualTo("CANCELLED");
        assertThat(response.getSummary().get("previousStatus").asText()).isEqualTo("QUEUED");
        assertThat(response.getSummary().get("executionEngineReady").asBoolean()).isTrue();
        assertThat(response.getCompletedAt()).isNotNull();
        verify(auditLogService).record(
                eq(userId),
                eq(AuditActionType.STRATEGY_BOT_RUN_CANCELLED),
                eq(AuditResourceType.STRATEGY_BOT_RUN),
                eq(runId),
                any());
    }

    @Test
    void cancelRun_shouldRejectCompletedRun() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .name("Completed Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("50"))
                .cooldownMinutes(30)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary("{\"phase\":\"completed\"}")
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> strategyBotRunService.cancelRun(botId, runId, userId))
                .isInstanceOfSatisfying(ApiRequestException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("strategy_bot_run_not_cancellable");
                    assertThat(ex.getMessage()).contains("before cancellation");
                });
        verify(strategyBotRunRepository, never()).save(any(StrategyBotRun.class));
    }

    @Test
    void getBotAnalytics_shouldAggregateRecentRunScorecards() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(UUID.randomUUID())
                .name("Analytics Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun positiveRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 12.5,
                          "netPnl": 12500,
                          "maxDrawdownPercent": 4.2,
                          "winRate": 66.7,
                          "tradeCount": 6,
                          "profitFactor": 1.9,
                          "expectancyPerTrade": 2083.33,
                          "timeInMarketPercent": 45.5,
                          "linkedPortfolioAligned": false,
                          "entryReasonCounts": {"price_above_ma_3": 2},
                          "exitReasonCounts": {"take_profit_hit": 2}
                        }
                        """)
                .build();

        StrategyBotRun negativeRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(3))
                .completedAt(LocalDateTime.now().minusHours(2))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": -4.5,
                          "netPnl": -4500,
                          "maxDrawdownPercent": 7.8,
                          "winRate": 25.0,
                          "tradeCount": 2,
                          "profitFactor": 0.6,
                          "expectancyPerTrade": -2250,
                          "timeInMarketPercent": 30.0,
                          "linkedPortfolioAligned": true,
                          "entryReasonCounts": {"price_above_ma_3": 1},
                          "exitReasonCounts": {"stop_loss_hit": 1}
                        }
                        """)
                .build();

        StrategyBotRun forwardRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .requestedAt(LocalDateTime.now().minusMinutes(10))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 2.1,
                          "netPnl": 2100,
                          "tradeCount": 1,
                          "timeInMarketPercent": 18.0,
                          "lastEvaluatedOpenTime": 1710000000,
                          "entryReasonCounts": {"price_above_ma_3": 1},
                          "exitReasonCounts": {}
                        }
                        """)
                .build();
        StrategyBotRun cancelledRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.CANCELLED)
                .requestedAt(LocalDateTime.now().minusMinutes(5))
                .completedAt(LocalDateTime.now().minusMinutes(1))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "phase": "cancelled",
                          "previousStatus": "QUEUED"
                        }
                        """)
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(botId, userId))
                .thenReturn(List.of(cancelledRun, forwardRun, positiveRun, negativeRun));

        StrategyBotAnalyticsResponse analytics = strategyBotRunService.getBotAnalytics(botId, userId, "ALL", null);

        assertThat(analytics.getTotalRuns()).isEqualTo(4);
        assertThat(analytics.getCompletedRuns()).isEqualTo(2);
        assertThat(analytics.getRunningRuns()).isEqualTo(1);
        assertThat(analytics.getCancelledRuns()).isEqualTo(1);
        assertThat(analytics.getBacktestRuns()).isEqualTo(3);
        assertThat(analytics.getForwardTestRuns()).isEqualTo(1);
        assertThat(analytics.getPositiveCompletedRuns()).isEqualTo(1);
        assertThat(analytics.getNegativeCompletedRuns()).isEqualTo(1);
        assertThat(analytics.getTotalSimulatedTrades()).isEqualTo(8);
        assertThat(analytics.getAvgReturnPercent()).isEqualTo(4.0);
        assertThat(analytics.getAvgTradeCount()).isEqualTo(4.0);
        assertThat(analytics.getBestRun()).isNotNull();
        assertThat(analytics.getBestRun().getId()).isEqualTo(positiveRun.getId());
        assertThat(analytics.getWorstRun()).isNotNull();
        assertThat(analytics.getWorstRun().getId()).isEqualTo(negativeRun.getId());
        assertThat(analytics.getActiveForwardRun()).isNotNull();
        assertThat(analytics.getActiveForwardRun().getId()).isEqualTo(forwardRun.getId());
        assertThat(analytics.getEntryDriverTotals()).containsEntry("price_above_ma_3", 4);
        assertThat(analytics.getExitDriverTotals()).containsEntry("take_profit_hit", 2);
        assertThat(analytics.getRecentScorecards()).hasSize(4);
    }

    @Test
    void buildBotAnalyticsExports_shouldWrapContextAndFlattenScorecards() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("Analytics Bot")
                .description("Deterministic breakout model")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun completedRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "executionEngineReady": true,
                          "returnPercent": 12.5,
                          "netPnl": 12500,
                          "maxDrawdownPercent": 4.2,
                          "winRate": 66.7,
                          "tradeCount": 6,
                          "profitFactor": 1.9,
                          "expectancyPerTrade": 2083.33,
                          "timeInMarketPercent": 45.5,
                          "linkedPortfolioAligned": false,
                          "entryReasonCounts": {"price_above_ma_3": 2},
                          "exitReasonCounts": {"take_profit_hit": 2}
                        }
                        """)
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(botId, userId))
                .thenReturn(List.of(completedRun));

        String json = strategyBotRunService.buildBotAnalyticsExportJson(botId, userId, "ALL", null);
        String csv = new String(strategyBotRunService.buildBotAnalyticsExportCsv(botId, userId, "ALL", null), StandardCharsets.UTF_8);

        assertThat(json).contains("\"name\" : \"Analytics Bot\"");
        assertThat(json).contains("\"analytics\"");
        assertThat(csv).contains("context,name,Analytics Bot");
        assertThat(csv).contains("summary,totalRuns,1");
        assertThat(csv).contains("summary,cancelledRuns,0");
        assertThat(csv).contains("entryDriver,price_above_ma_3,2");
        assertThat(csv).contains("recentScorecard,recent");
    }

    @Test
    void getBotAnalytics_shouldRespectRunModeAndLookbackFilters() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(UUID.randomUUID())
                .name("Scoped Analytics Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun staleBacktest = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(50))
                .completedAt(LocalDateTime.now().minusDays(49))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "returnPercent": 15.0,
                          "netPnl": 15000.0,
                          "tradeCount": 5,
                          "profitFactor": 2.1,
                          "entryReasonCounts": {"price_above_ma_3": 2}
                        }
                        """)
                .build();
        StrategyBotRun recentForward = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(3))
                .completedAt(LocalDateTime.now().minusDays(2))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .summary("""
                        {
                          "returnPercent": 3.5,
                          "netPnl": 3500.0,
                          "tradeCount": 2,
                          "profitFactor": 1.3,
                          "entryReasonCounts": {"price_above_ma_3": 1}
                        }
                        """)
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(botId, userId))
                .thenReturn(List.of(recentForward, staleBacktest));

        StrategyBotAnalyticsResponse analytics = strategyBotRunService.getBotAnalytics(botId, userId, "FORWARD_TEST", 30);

        assertThat(analytics.getTotalRuns()).isEqualTo(1);
        assertThat(analytics.getCompletedRuns()).isEqualTo(1);
        assertThat(analytics.getBacktestRuns()).isEqualTo(0);
        assertThat(analytics.getForwardTestRuns()).isEqualTo(1);
        assertThat(analytics.getAvgReturnPercent()).isEqualTo(3.5);
        assertThat(analytics.getTotalSimulatedTrades()).isEqualTo(2);
        assertThat(analytics.getRecentScorecards()).hasSize(1);
    }

    @Test
    void getBotBoard_shouldSortBotsByAverageReturn() {
        UUID userId = UUID.randomUUID();

        StrategyBot strongerBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Momentum Prime")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot weakerBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Mean Revert")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun strongerRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(strongerBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(3))
                .summary("""
                        {
                          "returnPercent": 12.5,
                          "netPnl": 12500.0,
                          "winRate": 75.0,
                          "tradeCount": 4,
                          "profitFactor": 2.4
                        }
                        """)
                .build();
        StrategyBotRun weakerRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(weakerBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(2))
                .summary("""
                        {
                          "returnPercent": -3.0,
                          "netPnl": -3000.0,
                          "winRate": 40.0,
                          "tradeCount": 5,
                          "profitFactor": 0.8
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByAvgReturnDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(strongerBot.getId(), weakerBot.getId()), PageRequest.of(0, 10), 2));
        when(strategyBotRepository.findAllById(List.of(strongerBot.getId(), weakerBot.getId())))
                .thenReturn(List.of(strongerBot, weakerBot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(weakerRun, strongerRun));

        var board = strategyBotRunService.getBotBoard(userId, PageRequest.of(0, 10), "AVG_RETURN", "DESC", "ALL", null);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(strongerBot.getId());
        assertThat(board.getContent().get(0).getAvgReturnPercent()).isEqualTo(12.5);
        assertThat(board.getContent().get(1).getStrategyBotId()).isEqualTo(weakerBot.getId());
        assertThat(board.getContent().get(1).getAvgReturnPercent()).isEqualTo(-3.0);
        ArgumentCaptor<Collection<UUID>> botIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(strategyBotRunRepository).findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(botIdsCaptor.capture(), eq(userId));
        assertThat(botIdsCaptor.getValue()).containsExactlyInAnyOrder(strongerBot.getId(), weakerBot.getId());
        verify(strategyBotRepository).findOwnedBotIdsOrderByAvgReturnDesc(eq(userId), any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(strongerBot.getId(), weakerBot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
        verify(strategyBotRunRepository, never()).findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(any(), eq(userId));
    }

    @Test
    void getBotBoard_shouldRespectRunModeAndLookbackFilters() {
        UUID userId = UUID.randomUUID();
        StrategyBot bot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .linkedPortfolioId(UUID.randomUUID())
                .name("Scoped Bot")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1H")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
                .cooldownMinutes(60)
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun oldBacktestRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(bot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(40))
                .summary("""
                        {
                          "returnPercent": 18.0,
                          "netPnl": 18000.0,
                          "winRate": 80.0,
                          "tradeCount": 6,
                          "profitFactor": 2.6
                        }
                        """)
                .build();
        StrategyBotRun recentForwardRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(bot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(2))
                .summary("""
                        {
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "winRate": 60.0,
                          "tradeCount": 2,
                          "profitFactor": 1.4
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByScopedAvgReturnDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bot.getId()), PageRequest.of(0, 10), 1));
        when(strategyBotRepository.findAllById(List.of(bot.getId())))
                .thenReturn(List.of(bot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(recentForwardRun, oldBacktestRun));

        var board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "AVG_RETURN",
                "DESC",
                "FORWARD_TEST",
                30);

        assertThat(board.getContent()).hasSize(1);
        assertThat(board.getContent().get(0).getTotalRuns()).isEqualTo(1);
        assertThat(board.getContent().get(0).getCompletedRuns()).isEqualTo(1);
        assertThat(board.getContent().get(0).getAvgReturnPercent()).isEqualTo(4.0);
        assertThat(board.getContent().get(0).getTotalSimulatedTrades()).isEqualTo(2);
        verify(strategyBotRepository).findOwnedBotIdsOrderByScopedAvgReturnDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(bot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getBotBoard_shouldUsePagedFastPathForLatestRequestedSort() {
        UUID userId = UUID.randomUUID();
        StrategyBot newerBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Newer")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot olderBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Older")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun newerRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(newerBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(1))
                .summary("""
                        {
                          "returnPercent": 5.0,
                          "netPnl": 5000.0,
                          "tradeCount": 2
                        }
                        """)
                .build();
        StrategyBotRun olderRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(olderBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(2))
                .summary("""
                        {
                          "returnPercent": 2.0,
                          "netPnl": 2000.0,
                          "tradeCount": 1
                        }
                        """)
                .build();

        when(strategyBotRepository.findByUserIdOrderByLatestRequestedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newerBot, olderBot), PageRequest.of(0, 10), 2));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(newerRun, olderRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "LATEST_REQUESTED_AT",
                "DESC",
                "ALL",
                null);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(newerBot.getId());
        verify(strategyBotRepository).findByUserIdOrderByLatestRequestedAtDesc(eq(userId), any(Pageable.class));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getBotBoard_shouldUsePagedFastPathForTotalSimulatedTradesSort() {
        UUID userId = UUID.randomUUID();
        StrategyBot busierBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Busier")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot quieterBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Quieter")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun busierRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(busierBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(3))
                .summary("""
                        {
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "tradeCount": 9
                        }
                        """)
                .build();
        StrategyBotRun quieterRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(quieterBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(1))
                .summary("""
                        {
                          "returnPercent": 7.0,
                          "netPnl": 7000.0,
                          "tradeCount": 2
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByTotalSimulatedTradesDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(busierBot.getId(), quieterBot.getId()), PageRequest.of(0, 10), 2));
        when(strategyBotRepository.findAllById(List.of(busierBot.getId(), quieterBot.getId())))
                .thenReturn(List.of(busierBot, quieterBot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(busierRun, quieterRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "TOTAL_SIMULATED_TRADES",
                "DESC",
                "ALL",
                null);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(busierBot.getId());
        assertThat(board.getContent().get(0).getTotalSimulatedTrades()).isEqualTo(9);
        assertThat(board.getContent().get(1).getStrategyBotId()).isEqualTo(quieterBot.getId());
        assertThat(board.getContent().get(1).getTotalSimulatedTrades()).isEqualTo(2);
        verify(strategyBotRepository).findOwnedBotIdsOrderByTotalSimulatedTradesDesc(eq(userId), any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(busierBot.getId(), quieterBot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getBotBoard_shouldUsePagedFastPathForAvgWinRateSort() {
        UUID userId = UUID.randomUUID();
        StrategyBot higherWinRateBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Higher Win")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot lowerWinRateBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Lower Win")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun higherRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(higherWinRateBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(2))
                .summary("""
                        {
                          "returnPercent": 6.0,
                          "netPnl": 6000.0,
                          "winRate": 78.0,
                          "tradeCount": 4,
                          "profitFactor": 1.9
                        }
                        """)
                .build();
        StrategyBotRun lowerRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(lowerWinRateBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(1))
                .summary("""
                        {
                          "returnPercent": 3.0,
                          "netPnl": 3000.0,
                          "winRate": 41.0,
                          "tradeCount": 2,
                          "profitFactor": 1.1
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByAvgWinRateDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(higherWinRateBot.getId(), lowerWinRateBot.getId()), PageRequest.of(0, 10), 2));
        when(strategyBotRepository.findAllById(List.of(higherWinRateBot.getId(), lowerWinRateBot.getId())))
                .thenReturn(List.of(higherWinRateBot, lowerWinRateBot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(higherRun, lowerRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "AVG_WIN_RATE",
                "DESC",
                "ALL",
                null);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(higherWinRateBot.getId());
        assertThat(board.getContent().get(0).getAvgWinRate()).isEqualTo(78.0);
        assertThat(board.getContent().get(1).getStrategyBotId()).isEqualTo(lowerWinRateBot.getId());
        assertThat(board.getContent().get(1).getAvgWinRate()).isEqualTo(41.0);
        verify(strategyBotRepository).findOwnedBotIdsOrderByAvgWinRateDesc(eq(userId), any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(higherWinRateBot.getId(), lowerWinRateBot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getBotBoard_shouldUseScopedPagedFastPathForTotalRunsSort() {
        UUID userId = UUID.randomUUID();
        StrategyBot activeBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Active")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot inactiveBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Inactive")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun activeScopedRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(activeBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .summary("""
                        {
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "tradeCount": 2
                        }
                        """)
                .build();
        StrategyBotRun inactiveOutOfScopeRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(inactiveBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(45))
                .completedAt(LocalDateTime.now().minusDays(44))
                .summary("""
                        {
                          "returnPercent": 7.0,
                          "netPnl": 7000.0,
                          "tradeCount": 3
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByScopedRunCountDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeBot.getId(), inactiveBot.getId()), PageRequest.of(0, 10), 2));
        when(strategyBotRepository.findAllById(List.of(activeBot.getId(), inactiveBot.getId())))
                .thenReturn(List.of(activeBot, inactiveBot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(activeScopedRun, inactiveOutOfScopeRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "TOTAL_RUNS",
                "DESC",
                "FORWARD_TEST",
                30);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(activeBot.getId());
        assertThat(board.getContent().get(0).getTotalRuns()).isEqualTo(1);
        assertThat(board.getContent().get(1).getStrategyBotId()).isEqualTo(inactiveBot.getId());
        assertThat(board.getContent().get(1).getTotalRuns()).isEqualTo(0);
        verify(strategyBotRepository).findOwnedBotIdsOrderByScopedRunCountDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(activeBot.getId(), inactiveBot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getBotBoard_shouldUseScopedPagedFastPathForAvgReturnSort() {
        UUID userId = UUID.randomUUID();
        StrategyBot activeBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Active")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot inactiveBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Inactive")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun activeScopedRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(activeBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .summary("""
                        {
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "tradeCount": 2
                        }
                        """)
                .build();
        StrategyBotRun inactiveOutOfScopeRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(inactiveBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(45))
                .completedAt(LocalDateTime.now().minusDays(44))
                .summary("""
                        {
                          "returnPercent": 11.0,
                          "netPnl": 11000.0,
                          "tradeCount": 4
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByScopedAvgReturnDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeBot.getId(), inactiveBot.getId()), PageRequest.of(0, 10), 2));
        when(strategyBotRepository.findAllById(List.of(activeBot.getId(), inactiveBot.getId())))
                .thenReturn(List.of(activeBot, inactiveBot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(activeScopedRun, inactiveOutOfScopeRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "AVG_RETURN",
                "DESC",
                "FORWARD_TEST",
                30);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(activeBot.getId());
        assertThat(board.getContent().get(0).getAvgReturnPercent()).isEqualTo(4.0);
        assertThat(board.getContent().get(1).getStrategyBotId()).isEqualTo(inactiveBot.getId());
        assertThat(board.getContent().get(1).getAvgReturnPercent()).isNull();
        verify(strategyBotRepository).findOwnedBotIdsOrderByScopedAvgReturnDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(activeBot.getId(), inactiveBot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void getBotBoard_shouldUseScopedPagedFastPathForTotalSimulatedTradesSort() {
        UUID userId = UUID.randomUUID();
        StrategyBot activeBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Active")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot inactiveBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Inactive")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.READY)
                .build();

        StrategyBotRun activeScopedRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(activeBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .summary("""
                        {
                          "returnPercent": 4.0,
                          "netPnl": 4000.0,
                          "tradeCount": 5
                        }
                        """)
                .build();
        StrategyBotRun inactiveOutOfScopeRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(inactiveBot.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(45))
                .completedAt(LocalDateTime.now().minusDays(44))
                .summary("""
                        {
                          "returnPercent": 7.0,
                          "netPnl": 7000.0,
                          "tradeCount": 9
                        }
                        """)
                .build();

        when(strategyBotRepository.findOwnedBotIdsOrderByScopedTotalSimulatedTradesDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeBot.getId(), inactiveBot.getId()), PageRequest.of(0, 10), 2));
        when(strategyBotRepository.findAllById(List.of(activeBot.getId(), inactiveBot.getId())))
                .thenReturn(List.of(activeBot, inactiveBot));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(activeScopedRun, inactiveOutOfScopeRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                userId,
                PageRequest.of(0, 10),
                "TOTAL_SIMULATED_TRADES",
                "DESC",
                "FORWARD_TEST",
                30);

        assertThat(board.getContent()).hasSize(2);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(activeBot.getId());
        assertThat(board.getContent().get(0).getTotalSimulatedTrades()).isEqualTo(5);
        assertThat(board.getContent().get(1).getStrategyBotId()).isEqualTo(inactiveBot.getId());
        assertThat(board.getContent().get(1).getTotalSimulatedTrades()).isEqualTo(0);
        verify(strategyBotRepository).findOwnedBotIdsOrderByScopedTotalSimulatedTradesDesc(
                eq(userId),
                eq("FORWARD_TEST"),
                eq(true),
                any(LocalDateTime.class),
                any(Pageable.class));
        verify(strategyBotRepository).findAllById(List.of(activeBot.getId(), inactiveBot.getId()));
        verify(strategyBotRepository, never()).findByUserId(eq(userId), any(Pageable.class));
    }

    @Test
    void discoverPublicBotBoard_shouldUsePagedFastPathForTotalRunsSort() {
        UUID ownerId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();
        StrategyBot publicBot = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(ownerId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("Public Runner")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        AppUser owner = AppUser.builder()
                .id(ownerId)
                .username("runner")
                .displayName("Runner")
                .build();
        Portfolio linkedPortfolio = Portfolio.builder()
                .id(linkedPortfolioId)
                .name("Public Linked")
                .visibility(Portfolio.Visibility.PUBLIC)
                .build();
        StrategyBotRun firstRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(publicBot.getId())
                .userId(ownerId)
                .runMode(StrategyBotRun.RunMode.BACKTEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusHours(2))
                .summary("""
                        {
                          "returnPercent": 7.0,
                          "netPnl": 7000.0,
                          "tradeCount": 3
                        }
                        """)
                .build();
        StrategyBotRun secondRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(publicBot.getId())
                .userId(ownerId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .requestedAt(LocalDateTime.now().minusMinutes(30))
                .summary("""
                        {
                          "returnPercent": 1.2,
                          "netPnl": 1200.0,
                          "tradeCount": 1
                        }
                        """)
                .build();

        when(strategyBotRepository.findPublicDiscoverableBotsOrderByRunCountDesc(
                eq(Portfolio.Visibility.PUBLIC),
                eq(StrategyBot.Status.DRAFT),
                eq("btc"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(publicBot), PageRequest.of(0, 10), 1));
        when(userRepository.findByIdIn(List.of(ownerId))).thenReturn(List.of(owner));
        when(portfolioRepository.findByIdInAndVisibility(List.of(linkedPortfolioId), Portfolio.Visibility.PUBLIC))
                .thenReturn(List.of(linkedPortfolio));
        when(strategyBotRunRepository.findByStrategyBotIdInOrderByRequestedAtDesc(List.of(publicBot.getId())))
                .thenReturn(List.of(secondRun, firstRun));

        Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.discoverPublicBotBoard(
                PageRequest.of(0, 10),
                "TOTAL_RUNS",
                "DESC",
                "ALL",
                null,
                "btc");

        assertThat(board.getContent()).hasSize(1);
        assertThat(board.getContent().get(0).getStrategyBotId()).isEqualTo(publicBot.getId());
        assertThat(board.getContent().get(0).getTotalRuns()).isEqualTo(2);
        verify(strategyBotRepository).findPublicDiscoverableBotsOrderByRunCountDesc(
                eq(Portfolio.Visibility.PUBLIC),
                eq(StrategyBot.Status.DRAFT),
                eq("btc"),
                any(Pageable.class));
        verify(strategyBotRepository, never()).findPublicDiscoverableBots(any(), any(), any());
    }

    @Test
    void buildBotBoardExports_shouldReuseScopedSortedBoardEntries() throws Exception {
        UUID userId = UUID.randomUUID();

        StrategyBot leader = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Leader")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .status(StrategyBot.Status.READY)
                .build();
        StrategyBot idle = StrategyBot.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name("Idle")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .status(StrategyBot.Status.DRAFT)
                .build();

        StrategyBotRun recentForwardRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(leader.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(2))
                .completedAt(LocalDateTime.now().minusDays(1))
                .summary("""
                        {
                          "returnPercent": 6.0,
                          "netPnl": 6000.0,
                          "winRate": 66.0,
                          "tradeCount": 3,
                          "profitFactor": 1.7
                        }
                        """)
                .build();
        StrategyBotRun staleForwardRun = StrategyBotRun.builder()
                .id(UUID.randomUUID())
                .strategyBotId(idle.getId())
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .requestedAt(LocalDateTime.now().minusDays(45))
                .completedAt(LocalDateTime.now().minusDays(44))
                .summary("""
                        {
                          "returnPercent": 9.0,
                          "netPnl": 9000.0,
                          "winRate": 70.0,
                          "tradeCount": 2,
                          "profitFactor": 1.9
                        }
                        """)
                .build();

        StrategyBotBoardEntryResponse cachedLeader = StrategyBotBoardEntryResponse.builder()
                .strategyBotId(leader.getId())
                .name("Leader")
                .status("READY")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .totalRuns(1)
                .completedRuns(1)
                .avgReturnPercent(6.0)
                .build();
        StrategyBotBoardEntryResponse cachedIdle = StrategyBotBoardEntryResponse.builder()
                .strategyBotId(idle.getId())
                .name("Idle")
                .status("DRAFT")
                .market("CRYPTO")
                .symbol("ETHUSDT")
                .timeframe("4h")
                .totalRuns(0)
                .completedRuns(0)
                .build();
        String cachedJson = objectMapper.writeValueAsString(java.util.Map.of(
                "entries", List.of(cachedLeader, cachedIdle)));

        when(cacheService.get(anyString(), eq(String.class)))
                .thenReturn(Optional.empty(), Optional.of(cachedJson));
        when(strategyBotRepository.findByUserId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(idle, leader)));
        when(strategyBotRunRepository.findByStrategyBotIdInAndUserIdOrderByRequestedAtDesc(any(), eq(userId)))
                .thenReturn(List.of(staleForwardRun, recentForwardRun));

        String json = strategyBotRunService.buildBotBoardExportJson(userId, "AVG_RETURN", "DESC", "FORWARD_TEST", 30);
        String csv = new String(
                strategyBotRunService.buildBotBoardExportCsv(userId, "AVG_RETURN", "DESC", "FORWARD_TEST", 30),
                StandardCharsets.UTF_8);

        JsonNode payload = objectMapper.readTree(json);
        assertThat(payload.get("sortBy").asText()).isEqualTo("AVG_RETURN");
        assertThat(payload.get("direction").asText()).isEqualTo("DESC");
        assertThat(payload.get("runModeScope").asText()).isEqualTo("FORWARD_TEST");
        assertThat(payload.get("lookbackDays").asInt()).isEqualTo(30);
        assertThat(payload.get("entryCount").asInt()).isEqualTo(2);
        assertThat(payload.get("entries").size()).isEqualTo(2);
        assertThat(payload.get("entries").get(0).get("strategyBotId").asText()).isEqualTo(leader.getId().toString());
        assertThat(payload.get("entries").get(0).get("totalRuns").asInt()).isEqualTo(1);
        assertThat(payload.get("entries").get(1).get("strategyBotId").asText()).isEqualTo(idle.getId().toString());
        assertThat(payload.get("entries").get(1).get("totalRuns").asInt()).isEqualTo(0);

        assertThat(csv).contains("context,runModeScope,FORWARD_TEST");
        assertThat(csv).contains("context,lookbackDays,30");
        assertThat(csv).contains("boardEntry,Leader");
        assertThat(csv).contains("boardEntry,Idle");
        verify(strategyBotRepository, times(1)).findByUserId(eq(userId), any(Pageable.class));
        verify(cacheService).set(contains("strategy-bot:board:owned-export:user:" + userId), any(String.class), any(Duration.class));
    }

    @Test
    void buildRunExports_shouldWrapPersistedOutputsAndReconciliation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID linkedPortfolioId = UUID.randomUUID();

        StrategyBot bot = StrategyBot.builder()
                .id(botId)
                .userId(userId)
                .linkedPortfolioId(linkedPortfolioId)
                .name("Run Export Bot")
                .description("Persistent run export")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .timeframe("1h")
                .entryRules("{}")
                .exitRules("{}")
                .maxPositionSizePercent(new BigDecimal("20"))
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
                .requestedAt(LocalDateTime.now().minusHours(2))
                .completedAt(LocalDateTime.now().minusHours(1))
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary("""
                        {
                          "phase": "completed",
                          "executionEngineReady": true,
                          "tradeCount": 1,
                          "endingEquity": 101176.47,
                          "returnPercent": 4.5,
                          "entryReasonCounts": {"price_above_ma_20": 1},
                          "exitReasonCounts": {"take_profit_hit": 1},
                          "linkedPortfolioBalance": 100000.0,
                          "linkedPortfolioReferenceEquity": 101176.47,
                          "linkedPortfolioDrift": -1176.47,
                          "linkedPortfolioDriftPercent": -1.16,
                          "linkedPortfolioAligned": false
                        }
                        """)
                .build();

        StrategyBotRunFill fill = StrategyBotRunFill.builder()
                .strategyBotRunId(runId)
                .sequenceNo(1)
                .side("ENTRY")
                .openTime(Instant.now().toEpochMilli())
                .price(new BigDecimal("3200.00"))
                .quantity(new BigDecimal("12.50000000"))
                .realizedPnl(BigDecimal.ZERO.setScale(2))
                .matchedRules("[\"price_above_ma_20\"]")
                .build();

        StrategyBotRunEvent event = StrategyBotRunEvent.builder()
                .strategyBotRunId(runId)
                .sequenceNo(1)
                .openTime(Instant.now().toEpochMilli())
                .phase("ENTRY")
                .action("ENTERED")
                .closePrice(new BigDecimal("3200.00"))
                .cashBalance(new BigDecimal("50000.00"))
                .positionQuantity(new BigDecimal("12.50000000"))
                .equity(new BigDecimal("100000.00"))
                .matchedRules("[\"price_above_ma_20\"]")
                .details("{\"allocatedCapital\":50000.0}")
                .build();

        StrategyBotRunEquityPoint equityPoint = StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(runId)
                .sequenceNo(1)
                .openTime(Instant.now().toEpochMilli())
                .closePrice(new BigDecimal("3400.00"))
                .equity(new BigDecimal("101176.47"))
                .build();

        when(strategyBotService.getOwnedBotEntity(botId, userId)).thenReturn(bot);
        when(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)).thenReturn(Optional.of(run));
        when(strategyBotRunFillRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(eq(runId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fill)));
        when(strategyBotRunEventRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(eq(runId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(strategyBotRunEquityPointRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(eq(runId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(equityPoint)));
        when(portfolioRepository.findById(linkedPortfolioId)).thenReturn(Optional.of(Portfolio.builder()
                .id(linkedPortfolioId)
                .name("Linked Paper")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .build()));
        when(portfolioItemRepository.findByPortfolioId(linkedPortfolioId)).thenReturn(List.of());
        when(strategyBotRunEquityPointRepository.findFirstByStrategyBotRunIdOrderBySequenceNoDesc(runId))
                .thenReturn(Optional.of(equityPoint));

        String json = strategyBotRunService.buildRunExportJson(botId, runId, userId);
        String csv = new String(strategyBotRunService.buildRunExportCsv(botId, runId, userId), StandardCharsets.UTF_8);

        var payload = objectMapper.readTree(json);
        assertThat(payload.path("name").asText()).isEqualTo("Run Export Bot");
        assertThat(payload.path("run").path("id").asText()).isEqualTo(runId.toString());
        assertThat(payload.path("events").size()).isEqualTo(1);
        assertThat(payload.path("events").get(0).path("action").asText()).isEqualTo("ENTERED");
        assertThat(payload.path("fills").size()).isEqualTo(1);
        assertThat(payload.path("equityCurve").size()).isEqualTo(1);
        assertThat(payload.path("reconciliationPlan").path("linkedPortfolioId").asText()).isEqualTo(linkedPortfolioId.toString());
        assertThat(csv).contains("context,name,Run Export Bot");
        assertThat(csv).contains("summary,tradeCount,1");
        assertThat(csv).contains("event,ENTRY,ENTERED");
        assertThat(csv).contains("reconciliation,targetCashBalance,101176.47");
        assertThat(csv).contains("ENTRY");
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

    @Test
    void refreshForwardTestRunSystem_shouldFailRunWhenSystemRefreshThrows() {
        UUID userId = UUID.randomUUID();
        UUID botId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .strategyBotId(botId)
                .userId(userId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .effectiveInitialCapital(new BigDecimal("100000"))
                .compiledEntryRules("{\"all\":[\"price_above_ma_3\"]}")
                .compiledExitRules("{\"any\":[\"take_profit_hit\"]}")
                .summary("{}")
                .build();

        when(strategyBotRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(strategyBotService.getOwnedBotEntity(botId, userId))
                .thenThrow(new IllegalArgumentException("Strategy bot not found"));
        when(strategyBotRunRepository.save(any(StrategyBotRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StrategyBotRun failed = strategyBotRunService.refreshForwardTestRunSystem(runId);

        assertThat(failed).isNotNull();
        assertThat(failed.getStatus()).isEqualTo(StrategyBotRun.Status.FAILED);
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(failed.getErrorMessage()).contains("Strategy bot not found");
        verify(auditLogService, never()).record(any(), any(), any(), any(), any());
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
