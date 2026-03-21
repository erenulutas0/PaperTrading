package com.finance.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.StrategyBot;
import com.finance.core.domain.StrategyBotRun;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.dto.StrategyBotRunRequest;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StrategyBotRunService {

    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotService strategyBotService;
    private final StrategyBotRuleEngineService strategyBotRuleEngineService;
    private final PortfolioRepository portfolioRepository;
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
                .summary(buildQueuedSummary(runMode, bot))
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

        if (run.getRunMode() != StrategyBotRun.RunMode.BACKTEST) {
            throw new IllegalStateException("Only backtest execution is currently supported");
        }
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

        run.setStatus(StrategyBotRun.Status.RUNNING);
        run.setStartedAt(LocalDateTime.now());

        List<MarketCandleResponse> candles = loadBacktestCandles(bot, run);
        BacktestSummary summary = simulateBacktest(bot, run, candles);

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

    private String buildQueuedSummary(StrategyBotRun.RunMode runMode, StrategyBot bot) {
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

    private Map<String, Object> buildExecutionAuditDetails(StrategyBotRun run, StrategyBot bot, BacktestSummary summary) {
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

    private BacktestSummary simulateBacktest(StrategyBot bot, StrategyBotRun run, List<MarketCandleResponse> candles) {
        double cash = run.getEffectiveInitialCapital().doubleValue();
        double quantity = 0.0;
        double entryPrice = 0.0;
        boolean positionOpen = false;
        int winCount = 0;
        int lossCount = 0;
        List<Map<String, Object>> fills = new ArrayList<>();
        List<Map<String, Object>> equityCurve = new ArrayList<>();
        double peakEquity = cash;
        double maxDrawdownPercent = 0.0;
        long lastEntryOpenTime = Long.MIN_VALUE;
        long cooldownMillis = Math.max(bot.getCooldownMinutes(), 0L) * 60_000L;

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
                boolean shouldExit = exit.matched() || i == candles.size() - 1;
                if (shouldExit) {
                    double exitPrice = candle.getClose();
                    double proceeds = quantity * exitPrice;
                    double pnl = proceeds - (quantity * entryPrice);
                    cash += proceeds;
                    fills.add(fill("EXIT", candle, exitPrice, quantity, pnl, exit.matchedRules()));
                    if (pnl >= 0) {
                        winCount++;
                    } else {
                        lossCount++;
                    }
                    positionOpen = false;
                    quantity = 0.0;
                    entryPrice = 0.0;
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
                        fills.add(fill("ENTRY", candle, entryPrice, quantity, 0.0, entry.matchedRules()));
                    }
                }
            }

            double equity = cash + (positionOpen ? quantity * candle.getClose() : 0.0);
            peakEquity = Math.max(peakEquity, equity);
            if (peakEquity > 0.0) {
                maxDrawdownPercent = Math.max(maxDrawdownPercent, ((peakEquity - equity) / peakEquity) * 100.0);
            }
            equityCurve.add(equityPoint(candle, equity));
        }

        double startingCapital = run.getEffectiveInitialCapital().doubleValue();
        double endingEquity = equityCurve.isEmpty() ? startingCapital : ((Number) equityCurve.get(equityCurve.size() - 1).get("equity")).doubleValue();
        double netPnl = endingEquity - startingCapital;
        int tradeCount = winCount + lossCount;
        double returnPercent = startingCapital == 0.0 ? 0.0 : (netPnl / startingCapital) * 100.0;
        double winRate = tradeCount == 0 ? 0.0 : (winCount * 100.0) / tradeCount;

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", "completed");
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
        payload.put("fillCount", fills.size());
        payload.put("maxDrawdownPercent", round(maxDrawdownPercent));
        payload.put("candleCount", candles.size());
        payload.put("fills", fills);
        payload.put("equityCurve", equityCurve);

        return new BacktestSummary(
                payload,
                tradeCount,
                winCount,
                lossCount,
                round(endingEquity),
                round(netPnl),
                round(returnPercent),
                round(maxDrawdownPercent));
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

    private Map<String, Object> equityPoint(MarketCandleResponse candle, double equity) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("openTime", candle.getOpenTime());
        row.put("close", round(candle.getClose()));
        row.put("equity", round(equity));
        return row;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
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

    private record BacktestSummary(
            Map<String, Object> payload,
            int tradeCount,
            int winCount,
            int lossCount,
            double endingEquity,
            double netPnl,
            double returnPercent,
            double maxDrawdownPercent) {
    }
}
