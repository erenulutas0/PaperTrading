package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.domain.StrategyBotRunEquityPoint;
import com.finance.core.domain.StrategyBotRunFill;
import com.finance.core.domain.TradeActivity;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.dto.PublicStrategyBotDetailResponse;
import com.finance.core.dto.PublicStrategyBotRunDetailResponse;
import com.finance.core.dto.StrategyBotAnalyticsResponse;
import com.finance.core.dto.StrategyBotBoardEntryResponse;
import com.finance.core.dto.StrategyBotRunReconciliationResponse;
import com.finance.core.dto.StrategyBotRunEquityPointResponse;
import com.finance.core.dto.StrategyBotRunFillResponse;
import com.finance.core.dto.StrategyBotRunRequest;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.dto.StrategyBotRunScorecardResponse;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class StrategyBotRunService {

    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotRepository strategyBotRepository;
    private final StrategyBotService strategyBotService;
    private final StrategyBotRuleEngineService strategyBotRuleEngineService;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final StrategyBotRunFillRepository strategyBotRunFillRepository;
    private final StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final UserRepository userRepository;
    private final MarketDataFacadeService marketDataFacadeService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<StrategyBotRunResponse> getRuns(UUID botId, UUID userId, Pageable pageable) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        return strategyBotRunRepository.findByStrategyBotIdAndUserId(bot.getId(), userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public StrategyBotRunResponse getRun(UUID botId, UUID runId, UUID userId) {
        strategyBotService.getOwnedBotEntity(botId, userId);
        return toResponse(strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot run not found")));
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotRunFillResponse> getRunFills(UUID botId, UUID runId, UUID userId, Pageable pageable) {
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return strategyBotRunFillRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), pageable)
                .map(this::toFillResponse);
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotRunEquityPointResponse> getRunEquityCurve(UUID botId, UUID runId, UUID userId, Pageable pageable) {
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return strategyBotRunEquityPointRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), pageable)
                .map(this::toEquityPointResponse);
    }

    @Transactional(readOnly = true)
    public StrategyBotRunReconciliationResponse getRunReconciliation(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        return buildRunReconciliationState(bot, run).response();
    }

    @Transactional(readOnly = true)
    public StrategyBotAnalyticsResponse getBotAnalytics(UUID botId, UUID userId, String runMode, Integer lookbackDays) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        return buildBotAnalytics(
                bot,
                userId,
                normalizeBoardRunMode(runMode),
                normalizeBoardLookbackDays(lookbackDays));
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotBoardEntryResponse> getBotBoard(UUID userId,
                                                           Pageable pageable,
                                                           String sortBy,
                                                           String direction,
                                                           String runMode,
                                                           Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        List<StrategyBotBoardEntryResponse> entries = buildBotBoardEntries(
                userId,
                sortBy,
                direction,
                scopedRunMode,
                normalizedLookbackDays);

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= entries.size()) {
            return new PageImpl<>(List.of(), pageable, entries.size());
        }
        int end = Math.min(start + pageable.getPageSize(), entries.size());
        return new PageImpl<>(entries.subList(start, end), pageable, entries.size());
    }

    @Transactional(readOnly = true)
    public Page<StrategyBotBoardEntryResponse> discoverPublicBotBoard(Pageable pageable,
                                                                      String sortBy,
                                                                      String direction,
                                                                      String runMode,
                                                                      Integer lookbackDays,
                                                                      String query) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        List<StrategyBotBoardEntryResponse> entries = buildPublicBotBoardEntries(
                sortBy,
                direction,
                scopedRunMode,
                normalizedLookbackDays,
                query);

        int start = Math.toIntExact(pageable.getOffset());
        if (start >= entries.size()) {
            return new PageImpl<>(List.of(), pageable, entries.size());
        }
        int end = Math.min(start + pageable.getPageSize(), entries.size());
        return new PageImpl<>(entries.subList(start, end), pageable, entries.size());
    }

    @Transactional(readOnly = true)
    public PublicStrategyBotDetailResponse getPublicBotDetail(UUID botId,
                                                              String runMode,
                                                              Integer lookbackDays) {
        StrategyBot bot = strategyBotRepository.findPublicDiscoverableBotById(
                        botId,
                        Portfolio.Visibility.PUBLIC,
                        StrategyBot.Status.DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot not found"));
        AppUser owner = userRepository.findById(bot.getUserId()).orElse(null);
        Portfolio linkedPortfolio = bot.getLinkedPortfolioId() == null
                ? null
                : portfolioRepository.findById(bot.getLinkedPortfolioId())
                        .filter(portfolio -> portfolio.getVisibility() == Portfolio.Visibility.PUBLIC)
                        .orElse(null);
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        StrategyBotAnalyticsResponse analytics = buildBotAnalytics(
                bot,
                strategyBotRunRepository.findByStrategyBotIdOrderByRequestedAtDesc(bot.getId()),
                scopedRunMode,
                normalizedLookbackDays);

        return PublicStrategyBotDetailResponse.builder()
                .strategyBotId(bot.getId())
                .name(bot.getName())
                .description(bot.getDescription())
                .botKind(bot.getBotKind().name())
                .status(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .maxPositionSizePercent(bot.getMaxPositionSizePercent())
                .stopLossPercent(bot.getStopLossPercent())
                .takeProfitPercent(bot.getTakeProfitPercent())
                .cooldownMinutes(bot.getCooldownMinutes())
                .entryRules(parseJson(bot.getEntryRules()))
                .exitRules(parseJson(bot.getExitRules()))
                .analytics(analytics)
                .build();
    }

    @Transactional(readOnly = true)
    public PublicStrategyBotRunDetailResponse getPublicBotRunDetail(UUID botId, UUID runId) {
        StrategyBot bot = strategyBotRepository.findPublicDiscoverableBotById(
                        botId,
                        Portfolio.Visibility.PUBLIC,
                        StrategyBot.Status.DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot not found"));
        StrategyBotRun run = strategyBotRunRepository.findById(runId)
                .filter(candidate -> candidate.getStrategyBotId().equals(bot.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot run not found"));
        AppUser owner = userRepository.findById(bot.getUserId()).orElse(null);
        Portfolio linkedPortfolio = bot.getLinkedPortfolioId() == null
                ? null
                : portfolioRepository.findById(bot.getLinkedPortfolioId())
                        .filter(portfolio -> portfolio.getVisibility() == Portfolio.Visibility.PUBLIC)
                        .orElse(null);

        return PublicStrategyBotRunDetailResponse.builder()
                .strategyBotId(bot.getId())
                .runId(run.getId())
                .botName(bot.getName())
                .botDescription(bot.getDescription())
                .botStatus(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedInitialCapital(run.getRequestedInitialCapital())
                .effectiveInitialCapital(run.getEffectiveInitialCapital())
                .fromDate(run.getFromDate())
                .toDate(run.getToDate())
                .compiledEntryRules(parseJson(run.getCompiledEntryRules()))
                .compiledExitRules(parseJson(run.getCompiledExitRules()))
                .summary(parseJson(run.getSummary()))
                .errorMessage(run.getErrorMessage())
                .requestedAt(run.getRequestedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .fills(getRunFillRows(run))
                .equityCurve(getRunEquityPointRows(run))
                .build();
    }

    @Transactional(readOnly = true)
    public String buildPublicBotBoardExportJson(String sortBy,
                                                String direction,
                                                String runMode,
                                                Integer lookbackDays,
                                                String query) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        String normalizedQuery = normalizeSearchQuery(query);
        List<StrategyBotBoardEntryResponse> entries = buildPublicBotBoardEntries(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);

        LinkedHashMap<String, Object> payload = buildBoardExportPayload(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery,
                "PUBLIC");
        return writePrettyJsonExport(payload, "Failed to serialize public strategy bot board export");
    }

    @Transactional(readOnly = true)
    public byte[] buildPublicBotBoardExportCsv(String sortBy,
                                               String direction,
                                               String runMode,
                                               Integer lookbackDays,
                                               String query) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        String normalizedQuery = normalizeSearchQuery(query);
        List<StrategyBotBoardEntryResponse> entries = buildPublicBotBoardEntries(
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery);
        return buildBoardExportCsvBytes(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                normalizedQuery,
                "PUBLIC");
    }

    @Transactional(readOnly = true)
    public String buildPublicBotDetailExportJson(UUID botId, String runMode, Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        PublicStrategyBotDetailResponse detail = getPublicBotDetail(botId, runMode, lookbackDays);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", detail.getStrategyBotId());
        payload.put("name", detail.getName());
        payload.put("description", detail.getDescription());
        payload.put("botKind", detail.getBotKind());
        payload.put("status", detail.getStatus());
        payload.put("market", detail.getMarket());
        payload.put("symbol", detail.getSymbol());
        payload.put("timeframe", detail.getTimeframe());
        payload.put("linkedPortfolioId", detail.getLinkedPortfolioId());
        payload.put("linkedPortfolioName", detail.getLinkedPortfolioName());
        payload.put("ownerId", detail.getOwnerId());
        payload.put("ownerUsername", detail.getOwnerUsername());
        payload.put("ownerDisplayName", detail.getOwnerDisplayName());
        payload.put("ownerAvatarUrl", detail.getOwnerAvatarUrl());
        payload.put("ownerTrustScore", detail.getOwnerTrustScore());
        payload.put("maxPositionSizePercent", detail.getMaxPositionSizePercent());
        payload.put("stopLossPercent", detail.getStopLossPercent());
        payload.put("takeProfitPercent", detail.getTakeProfitPercent());
        payload.put("cooldownMinutes", detail.getCooldownMinutes());
        payload.put("runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        payload.put("lookbackDays", normalizedLookbackDays);
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("entryRules", detail.getEntryRules());
        payload.put("exitRules", detail.getExitRules());
        payload.put("analytics", detail.getAnalytics());
        return writePrettyJsonExport(payload, "Failed to serialize public strategy bot detail export");
    }

    @Transactional(readOnly = true)
    public byte[] buildPublicBotDetailExportCsv(UUID botId, String runMode, Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        PublicStrategyBotDetailResponse detail = getPublicBotDetail(botId, runMode, lookbackDays);
        StrategyBotAnalyticsResponse analytics = detail.getAnalytics();

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "completedAt",
                "returnPercent",
                "netPnl",
                "maxDrawdownPercent",
                "winRate",
                "tradeCount",
                "profitFactor",
                "expectancyPerTrade",
                "timeInMarketPercent",
                "linkedPortfolioAligned",
                "executionEngineReady",
                "lastEvaluatedOpenTime",
                "errorMessage"));

        addMetricRow(rows, "context", "strategyBotId", detail.getStrategyBotId());
        addMetricRow(rows, "context", "name", detail.getName());
        addMetricRow(rows, "context", "description", detail.getDescription());
        addMetricRow(rows, "context", "botKind", detail.getBotKind());
        addMetricRow(rows, "context", "status", detail.getStatus());
        addMetricRow(rows, "context", "market", detail.getMarket());
        addMetricRow(rows, "context", "symbol", detail.getSymbol());
        addMetricRow(rows, "context", "timeframe", detail.getTimeframe());
        addMetricRow(rows, "context", "linkedPortfolioId", detail.getLinkedPortfolioId());
        addMetricRow(rows, "context", "linkedPortfolioName", detail.getLinkedPortfolioName());
        addMetricRow(rows, "context", "ownerId", detail.getOwnerId());
        addMetricRow(rows, "context", "ownerUsername", detail.getOwnerUsername());
        addMetricRow(rows, "context", "ownerDisplayName", detail.getOwnerDisplayName());
        addMetricRow(rows, "context", "ownerTrustScore", detail.getOwnerTrustScore());
        addMetricRow(rows, "context", "maxPositionSizePercent", detail.getMaxPositionSizePercent());
        addMetricRow(rows, "context", "stopLossPercent", detail.getStopLossPercent());
        addMetricRow(rows, "context", "takeProfitPercent", detail.getTakeProfitPercent());
        addMetricRow(rows, "context", "cooldownMinutes", detail.getCooldownMinutes());
        addMetricRow(rows, "context", "runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        addMetricRow(rows, "context", "lookbackDays", normalizedLookbackDays);
        addMetricRow(rows, "context", "exportedAt", LocalDateTime.now());
        addMetricRow(rows, "rules", "entryRules", stringifyJson(detail.getEntryRules()));
        addMetricRow(rows, "rules", "exitRules", stringifyJson(detail.getExitRules()));

        addMetricRow(rows, "summary", "totalRuns", analytics.getTotalRuns());
        addMetricRow(rows, "summary", "backtestRuns", analytics.getBacktestRuns());
        addMetricRow(rows, "summary", "forwardTestRuns", analytics.getForwardTestRuns());
        addMetricRow(rows, "summary", "completedRuns", analytics.getCompletedRuns());
        addMetricRow(rows, "summary", "runningRuns", analytics.getRunningRuns());
        addMetricRow(rows, "summary", "failedRuns", analytics.getFailedRuns());
        addMetricRow(rows, "summary", "compilerReadyRuns", analytics.getCompilerReadyRuns());
        addMetricRow(rows, "summary", "positiveCompletedRuns", analytics.getPositiveCompletedRuns());
        addMetricRow(rows, "summary", "negativeCompletedRuns", analytics.getNegativeCompletedRuns());
        addMetricRow(rows, "summary", "totalSimulatedTrades", analytics.getTotalSimulatedTrades());
        addMetricRow(rows, "summary", "avgReturnPercent", analytics.getAvgReturnPercent());
        addMetricRow(rows, "summary", "avgNetPnl", analytics.getAvgNetPnl());
        addMetricRow(rows, "summary", "avgMaxDrawdownPercent", analytics.getAvgMaxDrawdownPercent());
        addMetricRow(rows, "summary", "avgWinRate", analytics.getAvgWinRate());
        addMetricRow(rows, "summary", "avgTradeCount", analytics.getAvgTradeCount());
        addMetricRow(rows, "summary", "avgProfitFactor", analytics.getAvgProfitFactor());
        addMetricRow(rows, "summary", "avgExpectancyPerTrade", analytics.getAvgExpectancyPerTrade());

        addScorecardRow(rows, "highlightRun", "bestRun", analytics.getBestRun());
        addScorecardRow(rows, "highlightRun", "worstRun", analytics.getWorstRun());
        addScorecardRow(rows, "highlightRun", "latestCompletedRun", analytics.getLatestCompletedRun());
        addScorecardRow(rows, "highlightRun", "activeForwardRun", analytics.getActiveForwardRun());

        addReasonRows(rows, "entryDriver", analytics.getEntryDriverTotals());
        addReasonRows(rows, "exitDriver", analytics.getExitDriverTotals());

        if (analytics.getRecentScorecards() != null) {
            for (StrategyBotRunScorecardResponse scorecard : analytics.getRecentScorecards()) {
                addScorecardRow(rows, "recentScorecard", "recent", scorecard);
            }
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public String buildBotBoardExportJson(UUID userId,
                                          String sortBy,
                                          String direction,
                                          String runMode,
                                          Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        List<StrategyBotBoardEntryResponse> entries = buildBotBoardEntries(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        LinkedHashMap<String, Object> payload = buildBoardExportPayload(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                null,
                "PRIVATE");
        return writePrettyJsonExport(payload, "Failed to serialize strategy bot board export");
    }

    @Transactional(readOnly = true)
    public byte[] buildBotBoardExportCsv(UUID userId,
                                         String sortBy,
                                         String direction,
                                         String runMode,
                                         Integer lookbackDays) {
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        String normalizedSort = normalizeBoardSort(sortBy);
        String normalizedDirection = normalizeBoardDirection(direction);
        List<StrategyBotBoardEntryResponse> entries = buildBotBoardEntries(
                userId,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays);
        return buildBoardExportCsvBytes(
                entries,
                normalizedSort,
                normalizedDirection,
                scopedRunMode,
                normalizedLookbackDays,
                null,
                "PRIVATE");
    }

    @Transactional(readOnly = true)
    public String buildBotAnalyticsExportJson(UUID botId, UUID userId, String runMode, Integer lookbackDays) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        StrategyBotAnalyticsResponse analytics = buildBotAnalytics(bot, userId, scopedRunMode, normalizedLookbackDays);
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", bot.getId());
        payload.put("name", bot.getName());
        payload.put("description", bot.getDescription());
        payload.put("market", bot.getMarket());
        payload.put("symbol", bot.getSymbol());
        payload.put("timeframe", bot.getTimeframe());
        payload.put("status", bot.getStatus().name());
        payload.put("linkedPortfolioId", bot.getLinkedPortfolioId());
        payload.put("runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        payload.put("lookbackDays", normalizedLookbackDays);
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("analytics", analytics);
        try {
            return objectMapper.copy()
                    .findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize strategy bot analytics export", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] buildBotAnalyticsExportCsv(UUID botId, UUID userId, String runMode, Integer lookbackDays) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun.RunMode scopedRunMode = normalizeBoardRunMode(runMode);
        Integer normalizedLookbackDays = normalizeBoardLookbackDays(lookbackDays);
        StrategyBotAnalyticsResponse analytics = buildBotAnalytics(bot, userId, scopedRunMode, normalizedLookbackDays);

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "completedAt",
                "returnPercent",
                "netPnl",
                "maxDrawdownPercent",
                "winRate",
                "tradeCount",
                "profitFactor",
                "expectancyPerTrade",
                "timeInMarketPercent",
                "linkedPortfolioAligned",
                "executionEngineReady",
                "lastEvaluatedOpenTime",
                "errorMessage"));

        addMetricRow(rows, "context", "strategyBotId", bot.getId());
        addMetricRow(rows, "context", "name", bot.getName());
        addMetricRow(rows, "context", "description", bot.getDescription());
        addMetricRow(rows, "context", "market", bot.getMarket());
        addMetricRow(rows, "context", "symbol", bot.getSymbol());
        addMetricRow(rows, "context", "timeframe", bot.getTimeframe());
        addMetricRow(rows, "context", "status", bot.getStatus().name());
        addMetricRow(rows, "context", "linkedPortfolioId", bot.getLinkedPortfolioId());
        addMetricRow(rows, "context", "runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        addMetricRow(rows, "context", "lookbackDays", normalizedLookbackDays);
        addMetricRow(rows, "context", "exportedAt", LocalDateTime.now());

        addMetricRow(rows, "summary", "totalRuns", analytics.getTotalRuns());
        addMetricRow(rows, "summary", "backtestRuns", analytics.getBacktestRuns());
        addMetricRow(rows, "summary", "forwardTestRuns", analytics.getForwardTestRuns());
        addMetricRow(rows, "summary", "completedRuns", analytics.getCompletedRuns());
        addMetricRow(rows, "summary", "runningRuns", analytics.getRunningRuns());
        addMetricRow(rows, "summary", "failedRuns", analytics.getFailedRuns());
        addMetricRow(rows, "summary", "compilerReadyRuns", analytics.getCompilerReadyRuns());
        addMetricRow(rows, "summary", "positiveCompletedRuns", analytics.getPositiveCompletedRuns());
        addMetricRow(rows, "summary", "negativeCompletedRuns", analytics.getNegativeCompletedRuns());
        addMetricRow(rows, "summary", "totalSimulatedTrades", analytics.getTotalSimulatedTrades());
        addMetricRow(rows, "summary", "avgReturnPercent", analytics.getAvgReturnPercent());
        addMetricRow(rows, "summary", "avgNetPnl", analytics.getAvgNetPnl());
        addMetricRow(rows, "summary", "avgMaxDrawdownPercent", analytics.getAvgMaxDrawdownPercent());
        addMetricRow(rows, "summary", "avgWinRate", analytics.getAvgWinRate());
        addMetricRow(rows, "summary", "avgTradeCount", analytics.getAvgTradeCount());
        addMetricRow(rows, "summary", "avgProfitFactor", analytics.getAvgProfitFactor());
        addMetricRow(rows, "summary", "avgExpectancyPerTrade", analytics.getAvgExpectancyPerTrade());

        addScorecardRow(rows, "highlightRun", "bestRun", analytics.getBestRun());
        addScorecardRow(rows, "highlightRun", "worstRun", analytics.getWorstRun());
        addScorecardRow(rows, "highlightRun", "latestCompletedRun", analytics.getLatestCompletedRun());
        addScorecardRow(rows, "highlightRun", "activeForwardRun", analytics.getActiveForwardRun());

        addReasonRows(rows, "entryDriver", analytics.getEntryDriverTotals());
        addReasonRows(rows, "exitDriver", analytics.getExitDriverTotals());

        if (analytics.getRecentScorecards() != null) {
            for (StrategyBotRunScorecardResponse scorecard : analytics.getRecentScorecards()) {
                addScorecardRow(rows, "recentScorecard", "recent", scorecard);
            }
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public String buildRunExportJson(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("strategyBotId", bot.getId());
        payload.put("name", bot.getName());
        payload.put("description", bot.getDescription());
        payload.put("market", bot.getMarket());
        payload.put("symbol", bot.getSymbol());
        payload.put("timeframe", bot.getTimeframe());
        payload.put("status", bot.getStatus().name());
        payload.put("linkedPortfolioId", bot.getLinkedPortfolioId());
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("run", toResponse(run));
        payload.put("fills", getRunFillRows(run));
        payload.put("equityCurve", getRunEquityPointRows(run));
        payload.put("reconciliationPlan", safeBuildRunReconciliation(bot, run));
        try {
            return objectMapper.copy()
                    .findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize strategy bot run export", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] buildRunExportCsv(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        StrategyBotRunResponse runResponse = toResponse(run);
        List<StrategyBotRunFillResponse> fills = getRunFillRows(run);
        List<StrategyBotRunEquityPointResponse> equityPoints = getRunEquityPointRows(run);
        StrategyBotRunReconciliationResponse reconciliation = safeBuildRunReconciliation(bot, run);

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "runId",
                "runMode",
                "status",
                "requestedAt",
                "startedAt",
                "completedAt",
                "openTime",
                "sequenceNo",
                "side",
                "price",
                "quantity",
                "realizedPnl",
                "equity",
                "closePrice",
                "portfolioAligned",
                "warnings",
                "details"));

        addRunExportMetricRow(rows, "context", "strategyBotId", bot.getId());
        addRunExportMetricRow(rows, "context", "name", bot.getName());
        addRunExportMetricRow(rows, "context", "description", bot.getDescription());
        addRunExportMetricRow(rows, "context", "market", bot.getMarket());
        addRunExportMetricRow(rows, "context", "symbol", bot.getSymbol());
        addRunExportMetricRow(rows, "context", "timeframe", bot.getTimeframe());
        addRunExportMetricRow(rows, "context", "status", bot.getStatus().name());
        addRunExportMetricRow(rows, "context", "linkedPortfolioId", bot.getLinkedPortfolioId());
        addRunExportMetricRow(rows, "context", "exportedAt", LocalDateTime.now());

        addRunExportMetricRow(rows, "run", "runId", runResponse.getId());
        addRunExportMetricRow(rows, "run", "runMode", runResponse.getRunMode());
        addRunExportMetricRow(rows, "run", "status", runResponse.getStatus());
        addRunExportMetricRow(rows, "run", "requestedInitialCapital", runResponse.getRequestedInitialCapital());
        addRunExportMetricRow(rows, "run", "effectiveInitialCapital", runResponse.getEffectiveInitialCapital());
        addRunExportMetricRow(rows, "run", "fromDate", runResponse.getFromDate());
        addRunExportMetricRow(rows, "run", "toDate", runResponse.getToDate());
        addRunExportMetricRow(rows, "run", "requestedAt", runResponse.getRequestedAt());
        addRunExportMetricRow(rows, "run", "startedAt", runResponse.getStartedAt());
        addRunExportMetricRow(rows, "run", "completedAt", runResponse.getCompletedAt());
        addRunExportMetricRow(rows, "run", "errorMessage", runResponse.getErrorMessage());

        addRunExportSummaryMetric(rows, runResponse.getSummary(), "phase");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "executionEngineReady");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "fillCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "endingEquity");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "netPnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "returnPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "tradeCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "winCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "lossCount");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "maxDrawdownPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgWinPnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgLossPnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "profitFactor");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "expectancyPerTrade");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "bestTradePnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "worstTradePnl");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgHoldHours");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "maxHoldHours");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "timeInMarketPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "avgExposurePercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioName");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioBalance");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioReferenceEquity");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioDrift");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioDriftPercent");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioReconciliationBaseline");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "linkedPortfolioAligned");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "positionOpen");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "openQuantity");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "openEntryPrice");
        addRunExportSummaryMetric(rows, runResponse.getSummary(), "lastEvaluatedOpenTime");
        addRunExportMetricRow(rows, "summary", "supportedFeatures", joinJsonArray(runResponse.getSummary().path("supportedFeatures")));
        addRunExportMetricRow(rows, "summary", "unsupportedRules", joinJsonArray(runResponse.getSummary().path("unsupportedRules")));
        addRunExportMetricRow(rows, "summary", "warnings", joinJsonArray(runResponse.getSummary().path("warnings")));
        addReasonMetricRows(rows, "entryDriver", runResponse.getSummary().path("entryReasonCounts"));
        addReasonMetricRows(rows, "exitDriver", runResponse.getSummary().path("exitReasonCounts"));

        for (StrategyBotRunFillResponse fill : fills) {
            List<Object> row = new ArrayList<>();
            row.add("fill");
            row.add("");
            row.add("");
            row.add(runResponse.getId());
            row.add(runResponse.getRunMode());
            row.add(runResponse.getStatus());
            row.add(runResponse.getRequestedAt());
            row.add(runResponse.getStartedAt());
            row.add(runResponse.getCompletedAt());
            row.add(fill.getOpenTime());
            row.add(fill.getSequenceNo());
            row.add(fill.getSide());
            row.add(fill.getPrice());
            row.add(fill.getQuantity());
            row.add(fill.getRealizedPnl());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(joinJsonArray(fill.getMatchedRules()));
            rows.add(row);
        }

        for (StrategyBotRunEquityPointResponse point : equityPoints) {
            List<Object> row = new ArrayList<>();
            row.add("equityPoint");
            row.add("");
            row.add("");
            row.add(runResponse.getId());
            row.add(runResponse.getRunMode());
            row.add(runResponse.getStatus());
            row.add(runResponse.getRequestedAt());
            row.add(runResponse.getStartedAt());
            row.add(runResponse.getCompletedAt());
            row.add(point.getOpenTime());
            row.add(point.getSequenceNo());
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add(point.getEquity());
            row.add(point.getClosePrice());
            row.add("");
            row.add("");
            row.add("");
            rows.add(row);
        }

        if (reconciliation != null) {
            addRunExportMetricRow(rows, "reconciliation", "linkedPortfolioId", reconciliation.getLinkedPortfolioId());
            addRunExportMetricRow(rows, "reconciliation", "linkedPortfolioName", reconciliation.getLinkedPortfolioName());
            addRunExportMetricRow(rows, "reconciliation", "symbol", reconciliation.getSymbol());
            addRunExportMetricRow(rows, "reconciliation", "runStatus", reconciliation.getRunStatus());
            addRunExportMetricRow(rows, "reconciliation", "targetPositionOpen", reconciliation.isTargetPositionOpen());
            addRunExportMetricRow(rows, "reconciliation", "targetQuantity", reconciliation.getTargetQuantity());
            addRunExportMetricRow(rows, "reconciliation", "targetAveragePrice", reconciliation.getTargetAveragePrice());
            addRunExportMetricRow(rows, "reconciliation", "targetLastPrice", reconciliation.getTargetLastPrice());
            addRunExportMetricRow(rows, "reconciliation", "targetCashBalance", reconciliation.getTargetCashBalance());
            addRunExportMetricRow(rows, "reconciliation", "targetEquity", reconciliation.getTargetEquity());
            addRunExportMetricRow(rows, "reconciliation", "currentCashBalance", reconciliation.getCurrentCashBalance());
            addRunExportMetricRow(rows, "reconciliation", "currentQuantity", reconciliation.getCurrentQuantity());
            addRunExportMetricRow(rows, "reconciliation", "currentAveragePrice", reconciliation.getCurrentAveragePrice());
            addRunExportMetricRow(rows, "reconciliation", "cashDelta", reconciliation.getCashDelta());
            addRunExportMetricRow(rows, "reconciliation", "quantityDelta", reconciliation.getQuantityDelta());
            addRunExportMetricRow(rows, "reconciliation", "cashAligned", reconciliation.isCashAligned());
            addRunExportMetricRow(rows, "reconciliation", "quantityAligned", reconciliation.isQuantityAligned());
            addRunExportMetricRow(rows, "reconciliation", "portfolioAligned", reconciliation.isPortfolioAligned());
            addRunExportMetricRow(rows, "reconciliation", "extraSymbolCount", reconciliation.getExtraSymbolCount());
            addRunExportMetricRow(rows, "reconciliation", "warnings", String.join(" | ", reconciliation.getWarnings()));
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(StrategyBot bot, UUID userId) {
        return buildBotAnalytics(bot, userId, null, null);
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(StrategyBot bot,
                                                           UUID userId,
                                                           StrategyBotRun.RunMode scopedRunMode,
                                                           Integer lookbackDays) {
        return buildBotAnalytics(
                bot,
                strategyBotRunRepository.findByStrategyBotIdAndUserIdOrderByRequestedAtDesc(bot.getId(), userId),
                scopedRunMode,
                lookbackDays);
    }

    private StrategyBotAnalyticsResponse buildBotAnalytics(StrategyBot bot,
                                                           List<StrategyBotRun> rawRuns,
                                                           StrategyBotRun.RunMode scopedRunMode,
                                                           Integer lookbackDays) {
        List<StrategyBotRun> runs = filterRuns(rawRuns, scopedRunMode, lookbackDays);
        List<StrategyBotRunScorecardResponse> scorecards = runs.stream()
                .map(this::toScorecard)
                .toList();
        List<StrategyBotRunScorecardResponse> completedScorecards = scorecards.stream()
                .filter(scorecard -> "COMPLETED".equals(scorecard.getStatus()))
                .toList();

        return StrategyBotAnalyticsResponse.builder()
                .strategyBotId(bot.getId())
                .totalRuns(runs.size())
                .backtestRuns((int) runs.stream().filter(run -> run.getRunMode() == StrategyBotRun.RunMode.BACKTEST).count())
                .forwardTestRuns((int) runs.stream().filter(run -> run.getRunMode() == StrategyBotRun.RunMode.FORWARD_TEST).count())
                .completedRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.COMPLETED).count())
                .runningRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.RUNNING).count())
                .failedRuns((int) runs.stream().filter(run -> run.getStatus() == StrategyBotRun.Status.FAILED).count())
                .compilerReadyRuns((int) scorecards.stream().filter(scorecard -> Boolean.TRUE.equals(scorecard.getExecutionEngineReady())).count())
                .positiveCompletedRuns((int) completedScorecards.stream().filter(scorecard -> scorecard.getReturnPercent() != null && scorecard.getReturnPercent() > 0.0).count())
                .negativeCompletedRuns((int) completedScorecards.stream().filter(scorecard -> scorecard.getReturnPercent() != null && scorecard.getReturnPercent() < 0.0).count())
                .totalSimulatedTrades(completedScorecards.stream()
                        .map(StrategyBotRunScorecardResponse::getTradeCount)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum())
                .avgReturnPercent(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getReturnPercent))
                .avgNetPnl(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getNetPnl))
                .avgMaxDrawdownPercent(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getMaxDrawdownPercent))
                .avgWinRate(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getWinRate))
                .avgTradeCount(averageDouble(completedScorecards, scorecard -> scorecard.getTradeCount() == null ? null : scorecard.getTradeCount().doubleValue()))
                .avgProfitFactor(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getProfitFactor))
                .avgExpectancyPerTrade(averageDouble(completedScorecards, StrategyBotRunScorecardResponse::getExpectancyPerTrade))
                .bestRun(maxBy(completedScorecards, StrategyBotRunScorecardResponse::getReturnPercent))
                .worstRun(minBy(completedScorecards, StrategyBotRunScorecardResponse::getReturnPercent))
                .latestCompletedRun(completedScorecards.isEmpty() ? null : completedScorecards.get(0))
                .activeForwardRun(scorecards.stream()
                        .filter(scorecard -> "FORWARD_TEST".equals(scorecard.getRunMode()) && "RUNNING".equals(scorecard.getStatus()))
                        .findFirst()
                        .orElse(null))
                .entryDriverTotals(aggregateReasonCounts(runs, "entryReasonCounts"))
                .exitDriverTotals(aggregateReasonCounts(runs, "exitReasonCounts"))
                .recentScorecards(scorecards.stream().limit(12).toList())
                .build();
    }

    private List<StrategyBotBoardEntryResponse> buildBotBoardEntries(UUID userId,
                                                                     String sortBy,
                                                                     String direction,
                                                                     StrategyBotRun.RunMode scopedRunMode,
                                                                     Integer lookbackDays) {
        return strategyBotRepository.findByUserId(userId, Pageable.unpaged())
                .stream()
                .map(bot -> toBoardEntry(bot, buildBotAnalytics(bot, userId, scopedRunMode, lookbackDays)))
                .sorted(resolveBoardComparator(sortBy, direction))
                .toList();
    }

    private List<StrategyBotBoardEntryResponse> buildPublicBotBoardEntries(String sortBy,
                                                                           String direction,
                                                                           StrategyBotRun.RunMode scopedRunMode,
                                                                           Integer lookbackDays,
                                                                           String query) {
        List<StrategyBot> bots = strategyBotRepository.findPublicDiscoverableBots(
                Portfolio.Visibility.PUBLIC,
                StrategyBot.Status.DRAFT,
                normalizeSearchQuery(query));
        if (bots.isEmpty()) {
            return List.of();
        }

        Map<UUID, AppUser> ownersById = userRepository.findByIdIn(bots.stream()
                        .map(StrategyBot::getUserId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(LinkedHashMap::new,
                        (map, user) -> map.put(user.getId(), user),
                        LinkedHashMap::putAll);

        Map<UUID, Portfolio> publicPortfoliosById = portfolioRepository.findByIdInAndVisibility(
                        bots.stream()
                                .map(StrategyBot::getLinkedPortfolioId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList(),
                        Portfolio.Visibility.PUBLIC)
                .stream()
                .collect(LinkedHashMap::new,
                        (map, portfolio) -> map.put(portfolio.getId(), portfolio),
                        LinkedHashMap::putAll);

        Map<UUID, List<StrategyBotRun>> runsByBotId = new HashMap<>();
        strategyBotRunRepository.findByStrategyBotIdInOrderByRequestedAtDesc(
                        bots.stream().map(StrategyBot::getId).toList())
                .forEach(run -> runsByBotId
                        .computeIfAbsent(run.getStrategyBotId(), ignored -> new ArrayList<>())
                        .add(run));

        return bots.stream()
                .map(bot -> toBoardEntry(
                        bot,
                        buildBotAnalytics(bot, runsByBotId.getOrDefault(bot.getId(), List.of()), scopedRunMode, lookbackDays),
                        ownersById.get(bot.getUserId()),
                        publicPortfoliosById.get(bot.getLinkedPortfolioId())))
                .sorted(resolveBoardComparator(sortBy, direction))
                .toList();
    }

    private List<StrategyBotRun> filterRuns(List<StrategyBotRun> runs,
                                            StrategyBotRun.RunMode scopedRunMode,
                                            Integer lookbackDays) {
        LocalDateTime cutoff = lookbackDays == null ? null : LocalDateTime.now().minusDays(lookbackDays.longValue());
        return runs.stream()
                .filter(run -> scopedRunMode == null || run.getRunMode() == scopedRunMode)
                .filter(run -> {
                    if (cutoff == null) {
                        return true;
                    }
                    LocalDateTime anchor = run.getCompletedAt() != null ? run.getCompletedAt() : run.getRequestedAt();
                    return anchor != null && !anchor.isBefore(cutoff);
                })
                .toList();
    }

    private StrategyBotRun.RunMode normalizeBoardRunMode(String runMode) {
        if (runMode == null || runMode.isBlank() || "ALL".equalsIgnoreCase(runMode)) {
            return null;
        }
        try {
            return StrategyBotRun.RunMode.valueOf(runMode.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid strategy bot board run mode");
        }
    }

    private Integer normalizeBoardLookbackDays(Integer lookbackDays) {
        if (lookbackDays == null) {
            return null;
        }
        if (lookbackDays <= 0) {
            throw new IllegalArgumentException("Strategy bot board lookback days must be positive");
        }
        return lookbackDays;
    }

    private String normalizeBoardSort(String sortBy) {
        String normalizedSort = sortBy == null ? "AVG_RETURN" : sortBy.trim().toUpperCase();
        return switch (normalizedSort) {
            case "AVG_RETURN",
                 "AVG_NET_PNL",
                 "AVG_WIN_RATE",
                 "AVG_PROFIT_FACTOR",
                 "TOTAL_RUNS",
                 "TOTAL_SIMULATED_TRADES",
                 "LATEST_REQUESTED_AT" -> normalizedSort;
            default -> throw new IllegalArgumentException("Invalid strategy bot board sort");
        };
    }

    private String normalizeBoardDirection(String direction) {
        return "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";
    }

    private String normalizeSearchQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    private LinkedHashMap<String, Object> buildBoardExportPayload(List<StrategyBotBoardEntryResponse> entries,
                                                                  String normalizedSort,
                                                                  String normalizedDirection,
                                                                  StrategyBotRun.RunMode scopedRunMode,
                                                                  Integer normalizedLookbackDays,
                                                                  String normalizedQuery,
                                                                  String visibilityScope) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("visibilityScope", visibilityScope);
        payload.put("sortBy", normalizedSort);
        payload.put("direction", normalizedDirection);
        payload.put("runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        payload.put("lookbackDays", normalizedLookbackDays);
        payload.put("q", normalizedQuery == null || normalizedQuery.isBlank() ? null : normalizedQuery);
        payload.put("entryCount", entries.size());
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("entries", entries);
        return payload;
    }

    private byte[] buildBoardExportCsvBytes(List<StrategyBotBoardEntryResponse> entries,
                                            String normalizedSort,
                                            String normalizedDirection,
                                            StrategyBotRun.RunMode scopedRunMode,
                                            Integer normalizedLookbackDays,
                                            String normalizedQuery,
                                            String visibilityScope) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(
                "section",
                "key",
                "value",
                "strategyBotId",
                "name",
                "status",
                "market",
                "symbol",
                "timeframe",
                "totalRuns",
                "completedRuns",
                "runningRuns",
                "failedRuns",
                "totalSimulatedTrades",
                "avgReturnPercent",
                "avgNetPnl",
                "avgMaxDrawdownPercent",
                "avgWinRate",
                "avgProfitFactor",
                "avgExpectancyPerTrade",
                "latestRequestedAt",
                "bestRunId",
                "latestCompletedRunId",
                "activeForwardRunId"));

        addBoardMetricRow(rows, "context", "visibilityScope", visibilityScope);
        addBoardMetricRow(rows, "context", "sortBy", normalizedSort);
        addBoardMetricRow(rows, "context", "direction", normalizedDirection);
        addBoardMetricRow(rows, "context", "runModeScope", scopedRunMode == null ? "ALL" : scopedRunMode.name());
        addBoardMetricRow(rows, "context", "lookbackDays", normalizedLookbackDays);
        addBoardMetricRow(rows, "context", "q", normalizedQuery == null || normalizedQuery.isBlank() ? "" : normalizedQuery);
        addBoardMetricRow(rows, "context", "entryCount", entries.size());
        addBoardMetricRow(rows, "context", "exportedAt", LocalDateTime.now());

        entries.forEach(entry -> addBoardEntryRow(rows, entry));

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private String writePrettyJsonExport(Object payload, String errorMessage) {
        try {
            return objectMapper.copy()
                    .findAndRegisterModules()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(errorMessage, ex);
        }
    }

    private String stringifyJson(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private StrategyBotBoardEntryResponse toBoardEntry(StrategyBot bot, StrategyBotAnalyticsResponse analytics) {
        return toBoardEntry(bot, analytics, null, null);
    }

    private StrategyBotBoardEntryResponse toBoardEntry(StrategyBot bot,
                                                       StrategyBotAnalyticsResponse analytics,
                                                       AppUser owner,
                                                       Portfolio linkedPortfolio) {
        LocalDateTime latestRequestedAt = analytics.getRecentScorecards() == null || analytics.getRecentScorecards().isEmpty()
                ? null
                : analytics.getRecentScorecards().get(0).getRequestedAt();
        return StrategyBotBoardEntryResponse.builder()
                .strategyBotId(bot.getId())
                .description(bot.getDescription())
                .name(bot.getName())
                .status(bot.getStatus().name())
                .market(bot.getMarket())
                .symbol(bot.getSymbol())
                .timeframe(bot.getTimeframe())
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .linkedPortfolioName(linkedPortfolio == null ? null : linkedPortfolio.getName())
                .ownerId(owner == null ? bot.getUserId() : owner.getId())
                .ownerUsername(owner == null ? null : owner.getUsername())
                .ownerDisplayName(owner == null ? null : owner.getDisplayName())
                .ownerAvatarUrl(owner == null ? null : owner.getAvatarUrl())
                .ownerTrustScore(owner == null ? null : round(owner.getTrustScore()))
                .totalRuns(analytics.getTotalRuns())
                .completedRuns(analytics.getCompletedRuns())
                .runningRuns(analytics.getRunningRuns())
                .failedRuns(analytics.getFailedRuns())
                .totalSimulatedTrades(analytics.getTotalSimulatedTrades())
                .positiveCompletedRuns(analytics.getPositiveCompletedRuns())
                .negativeCompletedRuns(analytics.getNegativeCompletedRuns())
                .avgReturnPercent(analytics.getAvgReturnPercent())
                .avgNetPnl(analytics.getAvgNetPnl())
                .avgMaxDrawdownPercent(analytics.getAvgMaxDrawdownPercent())
                .avgWinRate(analytics.getAvgWinRate())
                .avgProfitFactor(analytics.getAvgProfitFactor())
                .avgExpectancyPerTrade(analytics.getAvgExpectancyPerTrade())
                .latestRequestedAt(latestRequestedAt)
                .bestRun(analytics.getBestRun())
                .latestCompletedRun(analytics.getLatestCompletedRun())
                .activeForwardRun(analytics.getActiveForwardRun())
                .build();
    }

    private Comparator<StrategyBotBoardEntryResponse> resolveBoardComparator(String sortBy, String direction) {
        String normalizedSort = normalizeBoardSort(sortBy);
        boolean ascending = "ASC".equalsIgnoreCase(direction);
        Comparator<StrategyBotBoardEntryResponse> comparator = switch (normalizedSort) {
            case "AVG_RETURN" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgReturnPercent, nullableDoubleComparator(ascending));
            case "AVG_NET_PNL" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgNetPnl, nullableDoubleComparator(ascending));
            case "AVG_WIN_RATE" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgWinRate, nullableDoubleComparator(ascending));
            case "AVG_PROFIT_FACTOR" -> Comparator.comparing(StrategyBotBoardEntryResponse::getAvgProfitFactor, nullableDoubleComparator(ascending));
            case "TOTAL_RUNS" -> Comparator.comparing(StrategyBotBoardEntryResponse::getTotalRuns, ascending ? Comparator.naturalOrder() : Comparator.reverseOrder());
            case "TOTAL_SIMULATED_TRADES" -> Comparator.comparing(StrategyBotBoardEntryResponse::getTotalSimulatedTrades, ascending ? Comparator.naturalOrder() : Comparator.reverseOrder());
            case "LATEST_REQUESTED_AT" -> Comparator.comparing(StrategyBotBoardEntryResponse::getLatestRequestedAt, nullableDateTimeComparator(ascending));
            default -> throw new IllegalArgumentException("Invalid strategy bot board sort");
        };

        return comparator
                .thenComparing(StrategyBotBoardEntryResponse::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(StrategyBotBoardEntryResponse::getStrategyBotId);
    }

    private Comparator<Double> nullableDoubleComparator(boolean ascending) {
        return Comparator.nullsLast(ascending ? Double::compareTo : Comparator.reverseOrder());
    }

    private Comparator<LocalDateTime> nullableDateTimeComparator(boolean ascending) {
        return Comparator.nullsLast(ascending ? LocalDateTime::compareTo : Comparator.reverseOrder());
    }

    private void addMetricRow(List<List<Object>> rows, String section, String key, Object value) {
        rows.add(List.of(section, key, value == null ? "" : value, "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""));
    }

    private void addBoardMetricRow(List<List<Object>> rows, String section, String key, Object value) {
        List<Object> row = new ArrayList<>();
        row.add(section);
        row.add(key);
        row.add(value == null ? "" : value);
        while (row.size() < 24) {
            row.add("");
        }
        rows.add(row);
    }

    private void addBoardEntryRow(List<List<Object>> rows, StrategyBotBoardEntryResponse entry) {
        List<Object> row = new ArrayList<>();
        row.add("boardEntry");
        row.add(entry.getName());
        row.add("");
        row.add(entry.getStrategyBotId());
        row.add(entry.getName());
        row.add(entry.getStatus());
        row.add(entry.getMarket());
        row.add(entry.getSymbol());
        row.add(entry.getTimeframe());
        row.add(entry.getTotalRuns());
        row.add(entry.getCompletedRuns());
        row.add(entry.getRunningRuns());
        row.add(entry.getFailedRuns());
        row.add(entry.getTotalSimulatedTrades());
        row.add(entry.getAvgReturnPercent());
        row.add(entry.getAvgNetPnl());
        row.add(entry.getAvgMaxDrawdownPercent());
        row.add(entry.getAvgWinRate());
        row.add(entry.getAvgProfitFactor());
        row.add(entry.getAvgExpectancyPerTrade());
        row.add(entry.getLatestRequestedAt());
        row.add(entry.getBestRun() == null ? "" : entry.getBestRun().getId());
        row.add(entry.getLatestCompletedRun() == null ? "" : entry.getLatestCompletedRun().getId());
        row.add(entry.getActiveForwardRun() == null ? "" : entry.getActiveForwardRun().getId());
        rows.add(row);
    }

    private void addRunExportMetricRow(List<List<Object>> rows, String section, String key, Object value) {
        List<Object> row = new ArrayList<>();
        row.add(section);
        row.add(key);
        row.add(value == null ? "" : value);
        while (row.size() < 20) {
            row.add("");
        }
        rows.add(row);
    }

    private void addRunExportSummaryMetric(List<List<Object>> rows, JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return;
        }
        addRunExportMetricRow(rows, "summary", fieldName, node.isContainerNode() ? node.toString() : node.asText());
    }

    private void addReasonRows(List<List<Object>> rows, String section, Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return;
        }
        counts.forEach((key, value) -> addMetricRow(rows, section, key, value));
    }

    private void addReasonMetricRows(List<List<Object>> rows, String section, JsonNode counts) {
        if (counts == null || !counts.isObject()) {
            return;
        }
        counts.fields().forEachRemaining(entry -> addRunExportMetricRow(rows, section, entry.getKey(), entry.getValue().asInt(0)));
    }

    private void addScorecardRow(List<List<Object>> rows, String section, String key, StrategyBotRunScorecardResponse scorecard) {
        if (scorecard == null) {
            return;
        }
        List<Object> row = new ArrayList<>();
        row.add(section);
        row.add(key);
        row.add("");
        row.add(scorecard.getId());
        row.add(scorecard.getRunMode());
        row.add(scorecard.getStatus());
        row.add(scorecard.getRequestedAt());
        row.add(scorecard.getCompletedAt());
        row.add(scorecard.getReturnPercent());
        row.add(scorecard.getNetPnl());
        row.add(scorecard.getMaxDrawdownPercent());
        row.add(scorecard.getWinRate());
        row.add(scorecard.getTradeCount());
        row.add(scorecard.getProfitFactor());
        row.add(scorecard.getExpectancyPerTrade());
        row.add(scorecard.getTimeInMarketPercent());
        row.add(scorecard.getLinkedPortfolioAligned());
        row.add(scorecard.getExecutionEngineReady());
        row.add(scorecard.getLastEvaluatedOpenTime());
        row.add(scorecard.getErrorMessage());
        rows.add(row);
    }

    @Transactional
    public StrategyBotRunReconciliationResponse applyRunReconciliation(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = getOwnedRunEntity(botId, runId, userId);
        if (run.getStatus() != StrategyBotRun.Status.RUNNING && run.getStatus() != StrategyBotRun.Status.COMPLETED) {
            throw new IllegalStateException("Strategy bot run must be RUNNING or COMPLETED before reconciliation");
        }

        RunReconciliationState state = buildRunReconciliationState(bot, run);
        StrategyBotRunReconciliationResponse plan = state.response();
        if (!plan.getWarnings().isEmpty()) {
            throw new IllegalStateException("Strategy bot reconciliation requires manual cleanup");
        }
        if (plan.isTargetPositionOpen() && plan.getTargetQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Strategy bot reconciliation target quantity is invalid");
        }
        if (plan.isTargetPositionOpen() && plan.getTargetLastPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Strategy bot reconciliation target price is unavailable");
        }
        if (plan.isPortfolioAligned()) {
            return plan;
        }

        Portfolio linkedPortfolio = state.linkedPortfolio();
        BigDecimal previousCashBalance = linkedPortfolio.getBalance().setScale(2, RoundingMode.HALF_UP);
        linkedPortfolio.setBalance(plan.getTargetCashBalance());
        portfolioRepository.save(linkedPortfolio);

        if (plan.isTargetPositionOpen()) {
            PortfolioItem targetItem = state.matchingItems().isEmpty()
                    ? PortfolioItem.builder()
                    .portfolio(linkedPortfolio)
                    .symbol(bot.getSymbol().toUpperCase())
                    .side("LONG")
                    .leverage(1)
                    .build()
                    : state.matchingItems().get(0);
            targetItem.setPortfolio(linkedPortfolio);
            targetItem.setSymbol(bot.getSymbol().toUpperCase());
            targetItem.setSide("LONG");
            targetItem.setLeverage(1);
            targetItem.setQuantity(plan.getTargetQuantity());
            targetItem.setAveragePrice(plan.getTargetAveragePrice());
            portfolioItemRepository.save(targetItem);
        } else {
            state.matchingItems().forEach(portfolioItemRepository::delete);
        }

        recordSyntheticReconciliationTrades(linkedPortfolio.getId(), plan);

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("strategyBotId", bot.getId());
        details.put("runId", run.getId());
        details.put("linkedPortfolioId", linkedPortfolio.getId());
        details.put("previousCashBalance", previousCashBalance);
        details.put("targetCashBalance", plan.getTargetCashBalance());
        details.put("previousQuantity", plan.getCurrentQuantity());
        details.put("targetQuantity", plan.getTargetQuantity());
        details.put("targetAveragePrice", plan.getTargetAveragePrice());
        details.put("targetPositionOpen", plan.isTargetPositionOpen());
        details.put("cashDelta", plan.getCashDelta());
        details.put("quantityDelta", plan.getQuantityDelta());
        details.put("journalParityEnabled", true);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_RECONCILED,
                AuditResourceType.STRATEGY_BOT_RUN,
                run.getId(),
                details);

        return buildRunReconciliationState(bot, run).response();
    }

    private void recordSyntheticReconciliationTrades(UUID portfolioId, StrategyBotRunReconciliationResponse plan) {
        BigDecimal quantityTolerance = new BigDecimal("0.00000001");
        BigDecimal priceTolerance = new BigDecimal("0.01");
        BigDecimal currentQuantity = plan.getCurrentQuantity().setScale(8, RoundingMode.HALF_UP);
        BigDecimal targetQuantity = plan.getTargetQuantity().setScale(8, RoundingMode.HALF_UP);
        BigDecimal quantityDelta = targetQuantity.subtract(currentQuantity).setScale(8, RoundingMode.HALF_UP);

        if (quantityDelta.compareTo(quantityTolerance) > 0) {
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase())
                    .type("BUY (BOT SYNC)")
                    .side("LONG")
                    .quantity(quantityDelta)
                    .price(plan.getTargetAveragePrice())
                    .realizedPnl(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        if (quantityDelta.compareTo(quantityTolerance.negate()) < 0) {
            BigDecimal sellQuantity = quantityDelta.abs().setScale(8, RoundingMode.HALF_UP);
            BigDecimal exitPrice = plan.getTargetLastPrice().compareTo(BigDecimal.ZERO) > 0
                    ? plan.getTargetLastPrice()
                    : plan.getTargetAveragePrice();
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase())
                    .type("SELL (BOT SYNC)")
                    .side("LONG")
                    .quantity(sellQuantity)
                    .price(exitPrice)
                    .realizedPnl(null)
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        if (targetQuantity.compareTo(BigDecimal.ZERO) > 0
                && plan.getTargetAveragePrice().subtract(plan.getCurrentAveragePrice()).abs().compareTo(priceTolerance) >= 0) {
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase())
                    .type("REPRICE (BOT SYNC)")
                    .side("LONG")
                    .quantity(targetQuantity)
                    .price(plan.getTargetAveragePrice())
                    .realizedPnl(null)
                    .timestamp(LocalDateTime.now())
                    .build());
            return;
        }

        if (plan.getCashDelta().abs().compareTo(priceTolerance) >= 0) {
            tradeActivityRepository.save(TradeActivity.builder()
                    .portfolioId(portfolioId)
                    .symbol(plan.getSymbol().toUpperCase())
                    .type("CASH SYNC (BOT)")
                    .side("LONG")
                    .quantity(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP))
                    .price(plan.getTargetCashBalance())
                    .realizedPnl(null)
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }

    private RunReconciliationState buildRunReconciliationState(StrategyBot bot, StrategyBotRun run) {
        if (bot.getLinkedPortfolioId() == null) {
            throw new IllegalStateException("Strategy bot has no linked portfolio");
        }

        Portfolio linkedPortfolio = portfolioRepository.findById(bot.getLinkedPortfolioId())
                .orElseThrow(() -> new IllegalArgumentException("Linked portfolio not found"));
        JsonNode summary = parseJson(run.getSummary());

        boolean targetPositionOpen = summary.path("positionOpen").asBoolean(false);
        BigDecimal targetQuantity = decimalOrZero(summary.get("openQuantity"), 8);
        BigDecimal targetAveragePrice = decimalOrZero(summary.get("openEntryPrice"), 2);
        BigDecimal targetEquity = decimalOrZero(summary.get("endingEquity"), 2);
        BigDecimal targetLastPrice = strategyBotRunEquityPointRepository
                .findFirstByStrategyBotRunIdOrderBySequenceNoDesc(run.getId())
                .map(StrategyBotRunEquityPoint::getClosePrice)
                .orElse(BigDecimal.ZERO);
        BigDecimal targetMarketValue = targetPositionOpen
                ? targetQuantity.multiply(targetLastPrice).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetCashBalance = targetEquity.subtract(targetMarketValue).setScale(2, RoundingMode.HALF_UP);

        var items = portfolioItemRepository.findByPortfolioId(linkedPortfolio.getId());
        var matchingItems = items.stream()
                .filter(item -> item.getSymbol().equalsIgnoreCase(bot.getSymbol()))
                .toList();
        BigDecimal currentQuantity = matchingItems.stream()
                .map(item -> item.getQuantity() == null ? BigDecimal.ZERO : item.getQuantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentAveragePrice = matchingItems.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : matchingItems.get(0).getAveragePrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal currentCashBalance = linkedPortfolio.getBalance().setScale(2, RoundingMode.HALF_UP);
        BigDecimal quantityDelta = targetQuantity.subtract(currentQuantity).setScale(8, RoundingMode.HALF_UP);
        BigDecimal cashDelta = targetCashBalance.subtract(currentCashBalance).setScale(2, RoundingMode.HALF_UP);
        int extraSymbolCount = (int) items.stream()
                .filter(item -> !item.getSymbol().equalsIgnoreCase(bot.getSymbol()))
                .count();

        List<String> warnings = new ArrayList<>();
        if (extraSymbolCount > 0) {
            warnings.add("Linked portfolio contains symbols outside the bot symbol");
        }
        if (matchingItems.size() > 1) {
            warnings.add("Linked portfolio contains multiple rows for the bot symbol");
        }
        boolean hasNonLongRows = matchingItems.stream()
                .anyMatch(item -> item.getSide() != null && !"LONG".equalsIgnoreCase(item.getSide()));
        if (hasNonLongRows) {
            warnings.add("Linked portfolio contains non-LONG rows for the bot symbol");
        }
        boolean hasLeveragedRows = matchingItems.stream()
                .anyMatch(item -> item.getLeverage() != null && item.getLeverage() != 1);
        if (hasLeveragedRows) {
            warnings.add("Linked portfolio contains leveraged rows for the bot symbol");
        }
        if (!targetPositionOpen && currentQuantity.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("Linked portfolio is still holding the bot symbol while run target is flat");
        }

        boolean cashAligned = cashDelta.abs().compareTo(new BigDecimal("0.01")) < 0;
        boolean quantityAligned = quantityDelta.abs().compareTo(new BigDecimal("0.00000001")) < 0;
        boolean portfolioAligned = cashAligned && quantityAligned && warnings.isEmpty();

        StrategyBotRunReconciliationResponse response = StrategyBotRunReconciliationResponse.builder()
                .runId(run.getId())
                .strategyBotId(bot.getId())
                .linkedPortfolioId(linkedPortfolio.getId())
                .linkedPortfolioName(linkedPortfolio.getName())
                .symbol(bot.getSymbol())
                .runStatus(run.getStatus().name())
                .targetPositionOpen(targetPositionOpen)
                .targetQuantity(targetQuantity.setScale(8, RoundingMode.HALF_UP))
                .targetAveragePrice(targetAveragePrice.setScale(2, RoundingMode.HALF_UP))
                .targetLastPrice(targetLastPrice.setScale(2, RoundingMode.HALF_UP))
                .targetCashBalance(targetCashBalance)
                .targetEquity(targetEquity.setScale(2, RoundingMode.HALF_UP))
                .currentCashBalance(currentCashBalance)
                .currentQuantity(currentQuantity.setScale(8, RoundingMode.HALF_UP))
                .currentAveragePrice(currentAveragePrice)
                .quantityDelta(quantityDelta)
                .cashDelta(cashDelta)
                .cashAligned(cashAligned)
                .quantityAligned(quantityAligned)
                .portfolioAligned(portfolioAligned)
                .extraSymbolCount(extraSymbolCount)
                .warnings(warnings)
                .build();
        return new RunReconciliationState(linkedPortfolio, matchingItems, response);
    }

    @Transactional
    public StrategyBotRunResponse requestRun(UUID botId, UUID userId, StrategyBotRunRequest request) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        if (bot.getStatus() != StrategyBot.Status.READY) {
            throw new IllegalStateException("Strategy bot must be READY before requesting a run");
        }

        StrategyBotRun.RunMode runMode = resolveRunMode(request != null ? request.getRunMode() : null);
        LocalDate fromDate = request != null ? request.getFromDate() : null;
        LocalDate toDate = request != null ? request.getToDate() : null;
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Run start date must be on or before end date");
        }

        BigDecimal requestedInitialCapital = request != null ? request.getInitialCapital() : null;
        BigDecimal effectiveInitialCapital = resolveInitialCapital(bot, requestedInitialCapital);

        StrategyBotRun run = StrategyBotRun.builder()
                .strategyBotId(bot.getId())
                .userId(userId)
                .linkedPortfolioId(bot.getLinkedPortfolioId())
                .runMode(runMode)
                .status(StrategyBotRun.Status.QUEUED)
                .requestedInitialCapital(requestedInitialCapital)
                .effectiveInitialCapital(effectiveInitialCapital)
                .fromDate(fromDate)
                .toDate(toDate)
                .compiledEntryRules(bot.getEntryRules())
                .compiledExitRules(bot.getExitRules())
                .summary(buildQueuedSummary(runMode, bot, effectiveInitialCapital))
                .build();

        StrategyBotRun saved = strategyBotRunRepository.save(run);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_REQUESTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                buildAuditDetails(saved, bot));
        return toResponse(saved);
    }

    @Transactional
    public StrategyBotRunResponse executeRun(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot run not found"));

        if (run.getStatus() != StrategyBotRun.Status.QUEUED) {
            throw new IllegalStateException("Strategy bot run must be QUEUED before execution");
        }

        StrategyBotRuleEngineService.RuleCompilation compilation = strategyBotRuleEngineService.compile(
                parseJson(run.getCompiledEntryRules()),
                parseJson(run.getCompiledExitRules()),
                bot.getStopLossPercent(),
                bot.getTakeProfitPercent());
        if (!compilation.executionEngineReady()) {
            throw new IllegalStateException("Strategy bot run is not executable by current engine");
        }

        if (run.getRunMode() == StrategyBotRun.RunMode.FORWARD_TEST) {
            return startForwardTestRun(bot, run, userId);
        }
        if (run.getRunMode() != StrategyBotRun.RunMode.BACKTEST) {
            throw new IllegalStateException("Only backtest execution is currently supported");
        }

        return executeBacktestRun(bot, run, userId);
    }

    @Transactional
    public StrategyBotRunResponse refreshForwardTestRun(UUID botId, UUID runId, UUID userId) {
        StrategyBot bot = strategyBotService.getOwnedBotEntity(botId, userId);
        StrategyBotRun run = strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot run not found"));
        StrategyBotRun.Status previousStatus = run.getStatus();
        RunSimulationSummary summary = refreshForwardTestRunInternal(bot, run, false);
        StrategyBotRun saved = strategyBotRunRepository.save(run);
        if (previousStatus == StrategyBotRun.Status.RUNNING && saved.getStatus() == StrategyBotRun.Status.COMPLETED) {
            auditLogService.record(
                    userId,
                    AuditActionType.STRATEGY_BOT_RUN_EXECUTED,
                    AuditResourceType.STRATEGY_BOT_RUN,
                    saved.getId(),
                    buildExecutionAuditDetails(saved, bot, summary));
        }
        return toResponse(saved);
    }

    @Transactional
    public StrategyBotRun refreshForwardTestRunSystem(UUID runId) {
        StrategyBotRun run = strategyBotRunRepository.findById(runId).orElse(null);
        if (run == null || run.getRunMode() != StrategyBotRun.RunMode.FORWARD_TEST || run.getStatus() != StrategyBotRun.Status.RUNNING) {
            return null;
        }
        try {
            StrategyBot bot = strategyBotService.getOwnedBotEntity(run.getStrategyBotId(), run.getUserId());
            RunSimulationSummary summary = refreshForwardTestRunInternal(bot, run, false);
            StrategyBotRun saved = strategyBotRunRepository.save(run);
            if (saved.getStatus() == StrategyBotRun.Status.COMPLETED) {
                auditLogService.record(
                        saved.getUserId(),
                        AuditActionType.STRATEGY_BOT_RUN_EXECUTED,
                        AuditResourceType.STRATEGY_BOT_RUN,
                        saved.getId(),
                        buildExecutionAuditDetails(saved, bot, summary));
            }
            return saved;
        } catch (Exception ex) {
            run.setStatus(StrategyBotRun.Status.FAILED);
            run.setCompletedAt(LocalDateTime.now());
            run.setErrorMessage(ex.getMessage());
            return strategyBotRunRepository.save(run);
        }
    }

    private StrategyBotRunResponse executeBacktestRun(StrategyBot bot, StrategyBotRun run, UUID userId) {
        run.setStatus(StrategyBotRun.Status.RUNNING);
        run.setStartedAt(LocalDateTime.now());

        List<MarketCandleResponse> candles = loadBacktestCandles(bot, run);
        RunSimulationSummary summary = simulateRun(bot, run, candles, true, "completed");

        persistRunOutputs(run, summary);

        run.setStatus(StrategyBotRun.Status.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());
        run.setErrorMessage(null);
        run.setSummary(writeSummary(summary.payload()));

        StrategyBotRun saved = strategyBotRunRepository.save(run);
        auditLogService.record(
                userId,
                AuditActionType.STRATEGY_BOT_RUN_EXECUTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                buildExecutionAuditDetails(saved, bot, summary));
        return toResponse(saved);
    }

    private StrategyBotRunResponse startForwardTestRun(StrategyBot bot, StrategyBotRun run, UUID userId) {
        RunSimulationSummary summary = refreshForwardTestRunInternal(bot, run, true);
        StrategyBotRun saved = strategyBotRunRepository.save(run);
        auditLogService.record(
                userId,
                saved.getStatus() == StrategyBotRun.Status.COMPLETED
                        ? AuditActionType.STRATEGY_BOT_RUN_EXECUTED
                        : AuditActionType.STRATEGY_BOT_RUN_STARTED,
                AuditResourceType.STRATEGY_BOT_RUN,
                saved.getId(),
                saved.getStatus() == StrategyBotRun.Status.COMPLETED
                        ? buildExecutionAuditDetails(saved, bot, summary)
                        : buildForwardTestAuditDetails(saved, bot, summary));
        return toResponse(saved);
    }

    private RunSimulationSummary refreshForwardTestRunInternal(StrategyBot bot,
                                                               StrategyBotRun run,
                                                               boolean allowQueuedStart) {
        if (run.getRunMode() != StrategyBotRun.RunMode.FORWARD_TEST) {
            throw new IllegalStateException("Only forward-test refresh is currently supported");
        }
        if (run.getStatus() == StrategyBotRun.Status.QUEUED) {
            if (!allowQueuedStart) {
                throw new IllegalStateException("Strategy bot forward-test run must be RUNNING before refresh");
            }
            run.setStatus(StrategyBotRun.Status.RUNNING);
            run.setStartedAt(LocalDateTime.now());
        } else if (run.getStatus() != StrategyBotRun.Status.RUNNING) {
            throw new IllegalStateException("Strategy bot forward-test run must be RUNNING before refresh");
        }

        List<MarketCandleResponse> candles = loadBacktestCandles(bot, run);
        boolean completeNow = forwardTestWindowReachedEnd(candles, run.getToDate());
        RunSimulationSummary summary = simulateRun(bot, run, candles, completeNow, completeNow ? "completed" : "running");

        persistRunOutputs(run, summary);
        run.setSummary(writeSummary(summary.payload()));
        run.setErrorMessage(null);
        if (completeNow) {
            run.setStatus(StrategyBotRun.Status.COMPLETED);
            run.setCompletedAt(LocalDateTime.now());
        } else {
            run.setStatus(StrategyBotRun.Status.RUNNING);
            run.setCompletedAt(null);
        }
        return summary;
    }

    private BigDecimal resolveInitialCapital(StrategyBot bot, BigDecimal requestedInitialCapital) {
        if (requestedInitialCapital != null) {
            if (requestedInitialCapital.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Initial capital must be positive");
            }
            return requestedInitialCapital;
        }
        if (bot.getLinkedPortfolioId() != null) {
            Portfolio portfolio = portfolioRepository.findById(bot.getLinkedPortfolioId())
                    .orElseThrow(() -> new IllegalArgumentException("Linked portfolio not found"));
            return portfolio.getBalance();
        }
        return new BigDecimal("100000");
    }

    private StrategyBotRun.RunMode resolveRunMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return StrategyBotRun.RunMode.BACKTEST;
        }
        try {
            return StrategyBotRun.RunMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid strategy bot run mode");
        }
    }

    private String buildQueuedSummary(StrategyBotRun.RunMode runMode, StrategyBot bot, BigDecimal effectiveInitialCapital) {
        StrategyBotRuleEngineService.RuleCompilation compilation = strategyBotRuleEngineService.compile(
                parseJson(bot.getEntryRules()),
                parseJson(bot.getExitRules()),
                bot.getStopLossPercent(),
                bot.getTakeProfitPercent());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("phase", "queued");
        summary.put("runMode", runMode.name());
        summary.put("botStatus", bot.getStatus().name());
        summary.put("market", bot.getMarket());
        summary.put("symbol", bot.getSymbol());
        summary.put("timeframe", bot.getTimeframe());
        summary.put("executionEngineReady", compilation.executionEngineReady());
        summary.put("entryRuleCount", compilation.entryRuleCount());
        summary.put("exitRuleCount", compilation.exitRuleCount());
        summary.put("supportedEntryRuleCount", compilation.supportedEntryRuleCount());
        summary.put("supportedExitRuleCount", compilation.supportedExitRuleCount());
        summary.put("unsupportedRules", compilation.unsupportedRules());
        summary.put("warnings", compilation.warnings());
        summary.put("supportedFeatures", compilation.supportedFeatures());
        appendLinkedPortfolioReconciliation(summary, bot.getLinkedPortfolioId(), effectiveInitialCapital.doubleValue(), "initial_capital");
        return writeSummary(summary);
    }

    private Map<String, Object> buildAuditDetails(StrategyBotRun run, StrategyBot bot) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("strategyBotId", bot.getId());
        details.put("runMode", run.getRunMode().name());
        details.put("status", run.getStatus().name());
        details.put("market", bot.getMarket());
        details.put("symbol", bot.getSymbol());
        details.put("timeframe", bot.getTimeframe());
        details.put("linkedPortfolioId", run.getLinkedPortfolioId());
        details.put("requestedInitialCapital", run.getRequestedInitialCapital());
        details.put("effectiveInitialCapital", run.getEffectiveInitialCapital());
        details.put("fromDate", run.getFromDate());
        details.put("toDate", run.getToDate());
        return details;
    }

    private Map<String, Object> buildExecutionAuditDetails(StrategyBotRun run, StrategyBot bot, RunSimulationSummary summary) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(buildAuditDetails(run, bot));
        details.put("phase", "completed");
        details.put("tradeCount", summary.tradeCount());
        details.put("winCount", summary.winCount());
        details.put("lossCount", summary.lossCount());
        details.put("endingEquity", summary.endingEquity());
        details.put("netPnl", summary.netPnl());
        details.put("returnPercent", summary.returnPercent());
        details.put("maxDrawdownPercent", summary.maxDrawdownPercent());
        return details;
    }

    private Map<String, Object> buildForwardTestAuditDetails(StrategyBotRun run, StrategyBot bot, RunSimulationSummary summary) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>(buildAuditDetails(run, bot));
        details.put("phase", "running");
        details.put("tradeCount", summary.tradeCount());
        details.put("winCount", summary.winCount());
        details.put("lossCount", summary.lossCount());
        details.put("endingEquity", summary.endingEquity());
        details.put("netPnl", summary.netPnl());
        details.put("returnPercent", summary.returnPercent());
        details.put("maxDrawdownPercent", summary.maxDrawdownPercent());
        details.put("lastEvaluatedOpenTime", summary.lastEvaluatedOpenTime());
        return details;
    }

    private void persistRunOutputs(StrategyBotRun run, RunSimulationSummary summary) {
        strategyBotRunFillRepository.deleteByStrategyBotRunId(run.getId());
        strategyBotRunEquityPointRepository.deleteByStrategyBotRunId(run.getId());
        strategyBotRunFillRepository.flush();
        strategyBotRunEquityPointRepository.flush();
        strategyBotRunFillRepository.saveAll(summary.fillRows());
        strategyBotRunEquityPointRepository.saveAll(summary.equityPoints());
    }

    private List<MarketCandleResponse> loadBacktestCandles(StrategyBot bot, StrategyBotRun run) {
        List<MarketCandleResponse> rawCandles = marketDataFacadeService.getCandles(
                resolveMarketType(bot.getMarket()),
                bot.getSymbol(),
                "ALL",
                bot.getTimeframe().toLowerCase(),
                null,
                500);

        List<MarketCandleResponse> filtered = rawCandles.stream()
                .sorted(Comparator.comparingLong(MarketCandleResponse::getOpenTime))
                .filter(candle -> matchesDateWindow(candle, run.getFromDate(), run.getToDate()))
                .toList();

        if (filtered.size() < 20) {
            throw new IllegalStateException("Not enough candles to execute strategy bot run");
        }
        return filtered;
    }

    private boolean matchesDateWindow(MarketCandleResponse candle, LocalDate fromDate, LocalDate toDate) {
        LocalDate candleDate = Instant.ofEpochMilli(candle.getOpenTime())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        if (fromDate != null && candleDate.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !candleDate.isAfter(toDate);
    }

    private MarketType resolveMarketType(String market) {
        try {
            return MarketType.valueOf(market == null ? "CRYPTO" : market.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid strategy bot market");
        }
    }

    private boolean forwardTestWindowReachedEnd(List<MarketCandleResponse> candles, LocalDate toDate) {
        if (toDate == null || candles.isEmpty()) {
            return false;
        }
        LocalDate latestCandleDate = Instant.ofEpochMilli(candles.get(candles.size() - 1).getOpenTime())
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
        return !latestCandleDate.isBefore(toDate);
    }

    private RunSimulationSummary simulateRun(StrategyBot bot,
                                             StrategyBotRun run,
                                             List<MarketCandleResponse> candles,
                                             boolean closePositionAtEnd,
                                             String phase) {
        double cash = run.getEffectiveInitialCapital().doubleValue();
        double quantity = 0.0;
        double entryPrice = 0.0;
        boolean positionOpen = false;
        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        Double bestTradePnl = null;
        Double worstTradePnl = null;
        long currentEntryOpenTime = Long.MIN_VALUE;
        long totalHoldMillis = 0L;
        long maxHoldMillis = 0L;
        int inMarketSamples = 0;
        double exposurePercentSum = 0.0;
        Map<String, Integer> entryReasonCounts = new HashMap<>();
        Map<String, Integer> exitReasonCounts = new HashMap<>();
        List<Map<String, Object>> fills = new ArrayList<>();
        List<Map<String, Object>> equityCurve = new ArrayList<>();
        List<StrategyBotRunFill> fillRows = new ArrayList<>();
        List<StrategyBotRunEquityPoint> equityPointRows = new ArrayList<>();
        double peakEquity = cash;
        double maxDrawdownPercent = 0.0;
        long lastEntryOpenTime = Long.MIN_VALUE;
        long cooldownMillis = Math.max(bot.getCooldownMinutes(), 0L) * 60_000L;
        int fillSequence = 0;
        int equitySequence = 0;

        for (int i = 0; i < candles.size(); i++) {
            List<MarketCandleResponse> window = candles.subList(0, i + 1);
            MarketCandleResponse candle = candles.get(i);

            if (positionOpen) {
                StrategyBotRuleEngineService.SignalEvaluation exit = evaluateRulesSafely(
                        parseJson(run.getCompiledExitRules()),
                        window,
                        new StrategyBotRuleEngineService.PositionContext(
                                entryPrice,
                                false,
                                bot.getStopLossPercent(),
                                bot.getTakeProfitPercent()));
                boolean shouldExit = exit.matched() || (closePositionAtEnd && i == candles.size() - 1);
                if (shouldExit) {
                    double exitPrice = candle.getClose();
                    double proceeds = quantity * exitPrice;
                    double pnl = proceeds - (quantity * entryPrice);
                    long holdMillis = currentEntryOpenTime == Long.MIN_VALUE ? 0L : Math.max(0L, candle.getOpenTime() - currentEntryOpenTime);
                    cash += proceeds;
                    fills.add(fill("EXIT", candle, exitPrice, quantity, pnl, exit.matchedRules()));
                    fillRows.add(fillRow(run.getId(), ++fillSequence, "EXIT", candle, exitPrice, quantity, pnl, exit.matchedRules()));
                    if (pnl >= 0) {
                        winCount++;
                        grossProfit += pnl;
                    } else {
                        lossCount++;
                        grossLoss += Math.abs(pnl);
                    }
                    bestTradePnl = bestTradePnl == null ? pnl : Math.max(bestTradePnl, pnl);
                    worstTradePnl = worstTradePnl == null ? pnl : Math.min(worstTradePnl, pnl);
                    totalHoldMillis += holdMillis;
                    maxHoldMillis = Math.max(maxHoldMillis, holdMillis);
                    incrementReasonCounts(exitReasonCounts, exit.matchedRules(), closePositionAtEnd && i == candles.size() - 1 ? "end_of_window" : "manual_exit");
                    positionOpen = false;
                    quantity = 0.0;
                    entryPrice = 0.0;
                    currentEntryOpenTime = Long.MIN_VALUE;
                }
            } else if (i > 0 && (lastEntryOpenTime == Long.MIN_VALUE || candle.getOpenTime() - lastEntryOpenTime >= cooldownMillis)) {
                StrategyBotRuleEngineService.SignalEvaluation entry = evaluateRulesSafely(
                        parseJson(run.getCompiledEntryRules()),
                        window,
                        null);
                if (entry.matched()) {
                    double entryCash = cash * bot.getMaxPositionSizePercent().doubleValue() / 100.0;
                    if (entryCash > 0.0) {
                        entryPrice = candle.getClose();
                        quantity = entryCash / entryPrice;
                        cash -= entryCash;
                        positionOpen = true;
                        lastEntryOpenTime = candle.getOpenTime();
                        currentEntryOpenTime = candle.getOpenTime();
                        incrementReasonCounts(entryReasonCounts, entry.matchedRules(), "entry_signal");
                        fills.add(fill("ENTRY", candle, entryPrice, quantity, 0.0, entry.matchedRules()));
                        fillRows.add(fillRow(run.getId(), ++fillSequence, "ENTRY", candle, entryPrice, quantity, 0.0, entry.matchedRules()));
                    }
                }
            }

            double equity = cash + (positionOpen ? quantity * candle.getClose() : 0.0);
            if (positionOpen) {
                inMarketSamples++;
                double marketValue = quantity * candle.getClose();
                if (equity > 0.0) {
                    exposurePercentSum += (marketValue / equity) * 100.0;
                }
            }
            peakEquity = Math.max(peakEquity, equity);
            if (peakEquity > 0.0) {
                maxDrawdownPercent = Math.max(maxDrawdownPercent, ((peakEquity - equity) / peakEquity) * 100.0);
            }
            equityCurve.add(equityPoint(candle, equity));
            equityPointRows.add(equityPointRow(run.getId(), ++equitySequence, candle, equity));
        }

        double startingCapital = run.getEffectiveInitialCapital().doubleValue();
        double endingEquity = equityCurve.isEmpty() ? startingCapital : ((Number) equityCurve.get(equityCurve.size() - 1).get("equity")).doubleValue();
        double netPnl = endingEquity - startingCapital;
        int tradeCount = winCount + lossCount;
        double returnPercent = startingCapital == 0.0 ? 0.0 : (netPnl / startingCapital) * 100.0;
        double winRate = tradeCount == 0 ? 0.0 : (winCount * 100.0) / tradeCount;
        Double avgWinPnl = winCount == 0 ? null : round(grossProfit / winCount);
        Double avgLossPnl = lossCount == 0 ? null : round((-grossLoss) / lossCount);
        Double profitFactor = grossLoss == 0.0 ? null : round(grossProfit / grossLoss);
        Double expectancyPerTrade = tradeCount == 0 ? null : round(netPnl / tradeCount);
        Double avgHoldHours = tradeCount == 0 ? null : round(totalHoldMillis / 3_600_000.0 / tradeCount);
        Double maxHoldHours = tradeCount == 0 ? null : round(maxHoldMillis / 3_600_000.0);
        Double timeInMarketPercent = candles.isEmpty() ? null : round((inMarketSamples * 100.0) / candles.size());
        Double avgExposurePercent = inMarketSamples == 0 ? null : round(exposurePercentSum / inMarketSamples);
        Long lastEvaluatedOpenTime = candles.isEmpty() ? null : candles.get(candles.size() - 1).getOpenTime();

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", phase);
        payload.put("runMode", run.getRunMode().name());
        payload.put("executionEngineReady", true);
        payload.put("market", bot.getMarket());
        payload.put("symbol", bot.getSymbol());
        payload.put("timeframe", bot.getTimeframe());
        payload.put("startingCapital", round(startingCapital));
        payload.put("endingEquity", round(endingEquity));
        payload.put("netPnl", round(netPnl));
        payload.put("returnPercent", round(returnPercent));
        payload.put("tradeCount", tradeCount);
        payload.put("winCount", winCount);
        payload.put("lossCount", lossCount);
        payload.put("winRate", round(winRate));
        payload.put("grossProfit", round(grossProfit));
        payload.put("grossLoss", round(grossLoss));
        payload.put("avgWinPnl", avgWinPnl);
        payload.put("avgLossPnl", avgLossPnl);
        payload.put("profitFactor", profitFactor);
        payload.put("expectancyPerTrade", expectancyPerTrade);
        payload.put("bestTradePnl", bestTradePnl == null ? null : round(bestTradePnl));
        payload.put("worstTradePnl", worstTradePnl == null ? null : round(worstTradePnl));
        payload.put("avgHoldHours", avgHoldHours);
        payload.put("maxHoldHours", maxHoldHours);
        payload.put("timeInMarketPercent", timeInMarketPercent);
        payload.put("avgExposurePercent", avgExposurePercent);
        payload.put("entryReasonCounts", entryReasonCounts);
        payload.put("exitReasonCounts", exitReasonCounts);
        payload.put("positionOpen", positionOpen);
        payload.put("openQuantity", positionOpen ? round(quantity) : null);
        payload.put("openEntryPrice", positionOpen ? round(entryPrice) : null);
        payload.put("lastEvaluatedOpenTime", lastEvaluatedOpenTime);
        payload.put("fillCount", fills.size());
        payload.put("maxDrawdownPercent", round(maxDrawdownPercent));
        payload.put("candleCount", candles.size());
        payload.put("fills", fills);
        payload.put("equityCurve", equityCurve);
        appendLinkedPortfolioReconciliation(payload, bot.getLinkedPortfolioId(), endingEquity, "ending_equity");

        return new RunSimulationSummary(
                payload,
                fillRows,
                equityPointRows,
                tradeCount,
                winCount,
                lossCount,
                round(endingEquity),
                round(netPnl),
                round(returnPercent),
                round(maxDrawdownPercent),
                lastEvaluatedOpenTime);
    }

    private StrategyBotRuleEngineService.SignalEvaluation evaluateRulesSafely(
            JsonNode rules,
            List<MarketCandleResponse> candles,
            StrategyBotRuleEngineService.PositionContext positionContext) {
        try {
            return strategyBotRuleEngineService.evaluate(rules, candles, positionContext);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not enough candles")) {
                return new StrategyBotRuleEngineService.SignalEvaluation(false, List.of(), List.of(), List.of());
            }
            throw ex;
        }
    }

    private void incrementReasonCounts(Map<String, Integer> bucket, List<String> matchedRules, String fallback) {
        if (matchedRules == null || matchedRules.isEmpty()) {
            bucket.merge(fallback, 1, Integer::sum);
            return;
        }
        matchedRules.forEach(rule -> bucket.merge(rule, 1, Integer::sum));
    }

    private void appendLinkedPortfolioReconciliation(Map<String, Object> payload,
                                                     UUID linkedPortfolioId,
                                                     double referenceEquity,
                                                     String baseline) {
        if (linkedPortfolioId == null) {
            return;
        }
        Portfolio linkedPortfolio = portfolioRepository.findById(linkedPortfolioId).orElse(null);
        if (linkedPortfolio == null) {
            payload.put("linkedPortfolioMissing", true);
            return;
        }
        double linkedPortfolioBalance = linkedPortfolio.getBalance().doubleValue();
        double drift = linkedPortfolioBalance - referenceEquity;
        Double driftPercent = referenceEquity == 0.0 ? null : round((drift / referenceEquity) * 100.0);
        payload.put("linkedPortfolioId", linkedPortfolioId);
        payload.put("linkedPortfolioName", linkedPortfolio.getName());
        payload.put("linkedPortfolioBalance", round(linkedPortfolioBalance));
        payload.put("linkedPortfolioReferenceEquity", round(referenceEquity));
        payload.put("linkedPortfolioDrift", round(drift));
        payload.put("linkedPortfolioDriftPercent", driftPercent);
        payload.put("linkedPortfolioReconciliationBaseline", baseline);
        payload.put("linkedPortfolioAligned", Math.abs(drift) < 0.01d);
    }

    private StrategyBotRunScorecardResponse toScorecard(StrategyBotRun run) {
        JsonNode summary = parseJson(run.getSummary());
        return StrategyBotRunScorecardResponse.builder()
                .id(run.getId())
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedAt(run.getRequestedAt())
                .completedAt(run.getCompletedAt())
                .returnPercent(doubleValue(summary, "returnPercent"))
                .netPnl(doubleValue(summary, "netPnl"))
                .maxDrawdownPercent(doubleValue(summary, "maxDrawdownPercent"))
                .winRate(doubleValue(summary, "winRate"))
                .tradeCount(intValue(summary, "tradeCount"))
                .profitFactor(doubleValue(summary, "profitFactor"))
                .expectancyPerTrade(doubleValue(summary, "expectancyPerTrade"))
                .timeInMarketPercent(doubleValue(summary, "timeInMarketPercent"))
                .linkedPortfolioAligned(booleanValue(summary, "linkedPortfolioAligned"))
                .executionEngineReady(booleanValue(summary, "executionEngineReady"))
                .lastEvaluatedOpenTime(longValue(summary, "lastEvaluatedOpenTime"))
                .errorMessage(run.getErrorMessage())
                .build();
    }

    private Map<String, Integer> aggregateReasonCounts(List<StrategyBotRun> runs, String fieldName) {
        Map<String, Integer> counts = new HashMap<>();
        for (StrategyBotRun run : runs) {
            JsonNode node = parseJson(run.getSummary()).path(fieldName);
            if (!node.isObject()) {
                continue;
            }
            node.fields().forEachRemaining(entry -> counts.merge(entry.getKey(), entry.getValue().asInt(0), Integer::sum));
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getValue(), left.getValue());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .collect(LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);
    }

    private Double averageDouble(List<StrategyBotRunScorecardResponse> scorecards,
                                 Function<StrategyBotRunScorecardResponse, Double> extractor) {
        double sum = 0.0;
        int count = 0;
        for (StrategyBotRunScorecardResponse scorecard : scorecards) {
            Double value = extractor.apply(scorecard);
            if (value == null) {
                continue;
            }
            sum += value;
            count++;
        }
        if (count == 0) {
            return null;
        }
        return round(sum / count);
    }

    private StrategyBotRunScorecardResponse maxBy(List<StrategyBotRunScorecardResponse> scorecards,
                                                  Function<StrategyBotRunScorecardResponse, Double> extractor) {
        return scorecards.stream()
                .filter(scorecard -> extractor.apply(scorecard) != null)
                .max(Comparator.comparing(extractor::apply))
                .orElse(null);
    }

    private StrategyBotRunScorecardResponse minBy(List<StrategyBotRunScorecardResponse> scorecards,
                                                  Function<StrategyBotRunScorecardResponse, Double> extractor) {
        return scorecards.stream()
                .filter(scorecard -> extractor.apply(scorecard) != null)
                .min(Comparator.comparing(extractor::apply))
                .orElse(null);
    }

    private Double doubleValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return round(node.asDouble());
    }

    private Integer intValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asInt();
    }

    private Long longValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asLong();
    }

    private Boolean booleanValue(JsonNode summary, String fieldName) {
        JsonNode node = summary.get(fieldName);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asBoolean();
    }

    private BigDecimal decimalOrZero(JsonNode value, int scale) {
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(value.asDouble()).setScale(scale, RoundingMode.HALF_UP);
    }

    private StrategyBotRun getOwnedRunEntity(UUID botId, UUID runId, UUID userId) {
        strategyBotService.getOwnedBotEntity(botId, userId);
        return strategyBotRunRepository.findByIdAndStrategyBotIdAndUserId(runId, botId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Strategy bot run not found"));
    }

    private List<StrategyBotRunFillResponse> getRunFillRows(StrategyBotRun run) {
        return strategyBotRunFillRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), Pageable.unpaged())
                .stream()
                .map(this::toFillResponse)
                .toList();
    }

    private List<StrategyBotRunEquityPointResponse> getRunEquityPointRows(StrategyBotRun run) {
        return strategyBotRunEquityPointRepository.findByStrategyBotRunIdOrderBySequenceNoAsc(run.getId(), Pageable.unpaged())
                .stream()
                .map(this::toEquityPointResponse)
                .toList();
    }

    private StrategyBotRunReconciliationResponse safeBuildRunReconciliation(StrategyBot bot, StrategyBotRun run) {
        try {
            return buildRunReconciliationState(bot, run).response();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
    }

    private Map<String, Object> fill(String side,
                                     MarketCandleResponse candle,
                                     double price,
                                     double quantity,
                                     double realizedPnl,
                                     List<String> matchedRules) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("side", side);
        row.put("openTime", candle.getOpenTime());
        row.put("price", round(price));
        row.put("quantity", round(quantity));
        row.put("realizedPnl", round(realizedPnl));
        row.put("matchedRules", matchedRules);
        return row;
    }

    private StrategyBotRunFill fillRow(UUID runId,
                                       int sequenceNo,
                                       String side,
                                       MarketCandleResponse candle,
                                       double price,
                                       double quantity,
                                       double realizedPnl,
                                       List<String> matchedRules) {
        return StrategyBotRunFill.builder()
                .strategyBotRunId(runId)
                .sequenceNo(sequenceNo)
                .side(side)
                .openTime(candle.getOpenTime())
                .price(BigDecimal.valueOf(price).setScale(8, RoundingMode.HALF_UP))
                .quantity(BigDecimal.valueOf(quantity).setScale(8, RoundingMode.HALF_UP))
                .realizedPnl(BigDecimal.valueOf(round(realizedPnl)).setScale(2, RoundingMode.HALF_UP))
                .matchedRules(writeJsonArray(matchedRules))
                .build();
    }

    private Map<String, Object> equityPoint(MarketCandleResponse candle, double equity) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("openTime", candle.getOpenTime());
        row.put("close", round(candle.getClose()));
        row.put("equity", round(equity));
        return row;
    }

    private StrategyBotRunEquityPoint equityPointRow(UUID runId,
                                                     int sequenceNo,
                                                     MarketCandleResponse candle,
                                                     double equity) {
        return StrategyBotRunEquityPoint.builder()
                .strategyBotRunId(runId)
                .sequenceNo(sequenceNo)
                .openTime(candle.getOpenTime())
                .closePrice(BigDecimal.valueOf(candle.getClose()).setScale(8, RoundingMode.HALF_UP))
                .equity(BigDecimal.valueOf(round(equity)).setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String escapeCsv(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private String joinJsonArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (!node.isArray()) {
            return node.isValueNode() ? node.asText() : node.toString();
        }
        List<String> values = new ArrayList<>();
        node.forEach(entry -> values.add(entry.isValueNode() ? entry.asText() : entry.toString()));
        return String.join(" | ", values);
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize strategy bot run matched rules", ex);
        }
    }

    private String writeSummary(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize strategy bot run summary", ex);
        }
    }

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw == null || raw.isBlank() ? "{}" : raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to parse strategy bot run payload", ex);
        }
    }

    private StrategyBotRunResponse toResponse(StrategyBotRun run) {
        return StrategyBotRunResponse.builder()
                .id(run.getId())
                .strategyBotId(run.getStrategyBotId())
                .userId(run.getUserId())
                .linkedPortfolioId(run.getLinkedPortfolioId())
                .runMode(run.getRunMode().name())
                .status(run.getStatus().name())
                .requestedInitialCapital(run.getRequestedInitialCapital())
                .effectiveInitialCapital(run.getEffectiveInitialCapital())
                .fromDate(run.getFromDate())
                .toDate(run.getToDate())
                .compiledEntryRules(parseJson(run.getCompiledEntryRules()))
                .compiledExitRules(parseJson(run.getCompiledExitRules()))
                .summary(parseJson(run.getSummary()))
                .errorMessage(run.getErrorMessage())
                .requestedAt(run.getRequestedAt())
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }

    private StrategyBotRunFillResponse toFillResponse(StrategyBotRunFill row) {
        return StrategyBotRunFillResponse.builder()
                .id(row.getId())
                .strategyBotRunId(row.getStrategyBotRunId())
                .sequenceNo(row.getSequenceNo())
                .side(row.getSide())
                .openTime(row.getOpenTime())
                .price(row.getPrice())
                .quantity(row.getQuantity())
                .realizedPnl(row.getRealizedPnl())
                .matchedRules(parseJson(row.getMatchedRules()))
                .build();
    }

    private StrategyBotRunEquityPointResponse toEquityPointResponse(StrategyBotRunEquityPoint row) {
        return StrategyBotRunEquityPointResponse.builder()
                .id(row.getId())
                .strategyBotRunId(row.getStrategyBotRunId())
                .sequenceNo(row.getSequenceNo())
                .openTime(row.getOpenTime())
                .closePrice(row.getClosePrice())
                .equity(row.getEquity())
                .build();
    }

    private record RunSimulationSummary(
            Map<String, Object> payload,
            List<StrategyBotRunFill> fillRows,
            List<StrategyBotRunEquityPoint> equityPoints,
            int tradeCount,
            int winCount,
            int lossCount,
            double endingEquity,
            double netPnl,
            double returnPercent,
            double maxDrawdownPercent,
            Long lastEvaluatedOpenTime) {
    }

    private record RunReconciliationState(
            Portfolio linkedPortfolio,
            List<PortfolioItem> matchingItems,
            StrategyBotRunReconciliationResponse response) {
    }
}
