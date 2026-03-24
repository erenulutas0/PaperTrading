package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.domain.TradeActivity;
import com.finance.core.dto.MarketInstrumentResponse;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PerformanceAnalyticsService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final AnalysisPostRepository analysisPostRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final PortfolioRepository portfolioRepository;
    private final MarketDataFacadeService marketDataFacadeService;

    // ==================== RISK METRICS ====================

    /**
     * Max Drawdown (MDD) for a portfolio.
     * MDD = Max (Peak - Trough) / Peak
     */
    public double calculateMaxDrawdown(UUID portfolioId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId);
        if (snapshots.isEmpty())
            return 0.0;

        double maxDrawdown = 0.0;
        double peak = snapshots.get(0).getTotalEquity().doubleValue();

        for (PortfolioSnapshot snapshot : snapshots) {
            double equity = snapshot.getTotalEquity().doubleValue();
            if (equity > peak)
                peak = equity;
            double drawdown = peak > 0 ? ((peak - equity) / peak) : 0;
            if (drawdown > maxDrawdown)
                maxDrawdown = drawdown;
        }
        return maxDrawdown * 100.0;
    }

    /**
     * Win Rate from resolved analysis predictions.
     */
    public double calculateWinRate(UUID authorId) {
        long hits = analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId, AnalysisPost.Outcome.HIT);
        long missed = analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                AnalysisPost.Outcome.MISSED);
        long expired = analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                AnalysisPost.Outcome.EXPIRED);

        long total = hits + missed + expired;
        return total == 0 ? 0.0 : ((double) hits / total) * 100.0;
    }

    public long countResolvedPredictions(UUID authorId) {
        long hits = analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId, AnalysisPost.Outcome.HIT);
        long missed = analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                AnalysisPost.Outcome.MISSED);
        long expired = analysisPostRepository.countByAuthorIdAndOutcomeAndDeletedFalse(authorId,
                AnalysisPost.Outcome.EXPIRED);
        return hits + missed + expired;
    }

    /**
     * Sharpe Ratio (risk-free rate = 0).
     */
    public double calculateSharpeRatio(UUID portfolioId) {
        double[] returns = getSnapshotReturns(portfolioId);
        if (returns.length == 0)
            return 0.0;

        double avgReturn = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(r -> Math.pow(r - avgReturn, 2)).sum() / returns.length;
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0)
            return avgReturn > 0 ? 999.0 : 0.0;
        return avgReturn / stdDev;
    }

    /**
     * Sortino Ratio: like Sharpe but only penalizes downside volatility.
     */
    public double calculateSortinoRatio(UUID portfolioId) {
        double[] returns = getSnapshotReturns(portfolioId);
        if (returns.length == 0)
            return 0.0;

        double avgReturn = Arrays.stream(returns).average().orElse(0);
        double downsideVariance = Arrays.stream(returns)
                .filter(r -> r < 0)
                .map(r -> r * r)
                .sum() / returns.length;
        double downsideDeviation = Math.sqrt(downsideVariance);

        if (downsideDeviation == 0)
            return avgReturn > 0 ? 999.0 : 0.0;
        return avgReturn / downsideDeviation;
    }

    /**
     * Annualized volatility from snapshot returns.
     */
    public double calculateVolatility(UUID portfolioId) {
        double[] returns = getSnapshotReturns(portfolioId);
        if (returns.length < 2)
            return 0.0;

        double avgReturn = Arrays.stream(returns).average().orElse(0);
        double variance = Arrays.stream(returns).map(r -> Math.pow(r - avgReturn, 2)).sum() / (returns.length - 1);
        return Math.sqrt(variance) * Math.sqrt(252) * 100; // Annualized %
    }

    /**
     * Profit Factor = Total Profits / Total Losses (from realized PnL).
     */
    public double calculateProfitFactor(UUID portfolioId) {
        List<TradeActivity> trades = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(portfolioId);

        double totalProfit = 0;
        double totalLoss = 0;

        for (TradeActivity trade : trades) {
            if (trade.getRealizedPnl() != null) {
                double pnl = trade.getRealizedPnl().doubleValue();
                if (pnl > 0)
                    totalProfit += pnl;
                else
                    totalLoss += Math.abs(pnl);
            }
        }

        return totalLoss == 0 ? (totalProfit > 0 ? 999.0 : 0.0) : totalProfit / totalLoss;
    }

    // ==================== TRADE STATS ====================

    /**
     * Get comprehensive trade statistics for a portfolio.
     */
    public Map<String, Object> getTradeStats(UUID portfolioId) {
        ensurePortfolioExists(portfolioId);
        List<TradeActivity> trades = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(portfolioId);
        return buildTradeStats(trades);
    }

    private Map<String, Object> buildTradeStats(List<TradeActivity> trades) {
        int totalTrades = trades.size();
        int buyCount = 0, sellCount = 0;
        int longCount = 0, shortCount = 0;
        int profitableTrades = 0, losingTrades = 0;
        double totalPnl = 0;
        double bestTrade = 0, worstTrade = 0;
        double avgWin = 0, avgLoss = 0;
        double totalProfit = 0, totalLoss = 0;

        Map<String, Integer> symbolCounts = new HashMap<>();

        for (TradeActivity trade : trades) {
            String normalizedTradeType = normalizeTradeType(trade.getType());
            // Type counts
            if (normalizedTradeType.startsWith("BUY"))
                buyCount++;
            else if (normalizedTradeType.startsWith("SELL"))
                sellCount++;

            // Side counts
            if ("LONG".equalsIgnoreCase(trade.getSide()))
                longCount++;
            else if ("SHORT".equalsIgnoreCase(trade.getSide()))
                shortCount++;

            // Symbol frequency
            symbolCounts.merge(trade.getSymbol(), 1, Integer::sum);

            // PnL stats
            if (trade.getRealizedPnl() != null) {
                double pnl = trade.getRealizedPnl().doubleValue();
                totalPnl += pnl;
                if (pnl > 0) {
                    profitableTrades++;
                    totalProfit += pnl;
                    if (pnl > bestTrade)
                        bestTrade = pnl;
                } else if (pnl < 0) {
                    losingTrades++;
                    totalLoss += Math.abs(pnl);
                    if (pnl < worstTrade)
                        worstTrade = pnl;
                }
            }
        }

        avgWin = profitableTrades > 0 ? totalProfit / profitableTrades : 0;
        avgLoss = losingTrades > 0 ? totalLoss / losingTrades : 0;

        // Find most traded symbol
        String mostTraded = symbolCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");

        double tradeWinRate = (profitableTrades + losingTrades) > 0
                ? ((double) profitableTrades / (profitableTrades + losingTrades)) * 100
                : 0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTrades", totalTrades);
        stats.put("buyCount", buyCount);
        stats.put("sellCount", sellCount);
        stats.put("longCount", longCount);
        stats.put("shortCount", shortCount);
        stats.put("profitableTrades", profitableTrades);
        stats.put("losingTrades", losingTrades);
        stats.put("tradeWinRate", Math.round(tradeWinRate * 100.0) / 100.0);
        stats.put("totalPnl", Math.round(totalPnl * 100.0) / 100.0);
        stats.put("bestTrade", Math.round(bestTrade * 100.0) / 100.0);
        stats.put("worstTrade", Math.round(worstTrade * 100.0) / 100.0);
        stats.put("avgWin", Math.round(avgWin * 100.0) / 100.0);
        stats.put("avgLoss", Math.round(avgLoss * 100.0) / 100.0);
        stats.put("mostTradedSymbol", mostTraded);
        stats.put("symbolBreakdown", symbolCounts);

        return stats;
    }

    // ==================== EQUITY CURVE ====================

    /**
     * Get equity curve data points for charting.
     */
    public List<Map<String, Object>> getEquityCurve(UUID portfolioId) {
        ensurePortfolioExists(portfolioId);
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId);
        return buildEquityCurve(snapshots);
    }

    // ==================== FULL DASHBOARD ====================

    /**
     * Aggregate all analytics into a single dashboard response.
     */
    public Map<String, Object> getRiskMetrics(UUID portfolioId) {
        ensurePortfolioExists(portfolioId);
        return buildRiskMetrics(portfolioId);
    }

    public Map<String, Object> getFullAnalytics(UUID portfolioId) {
        Map<String, Object> analytics = new LinkedHashMap<>();
        Portfolio portfolio = loadPortfolioOrThrow(portfolioId);
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId);
        List<TradeActivity> trades = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(portfolioId);
        Map<String, Object> positionSummary = buildPositionSummary(portfolio, trades);
        List<Map<String, Object>> riskAttribution = buildRiskAttribution(portfolio);
        Map<String, Object> performanceWindows = buildPerformanceWindows(snapshots);
        Map<String, Object> periodExtremes = buildPeriodExtremes(snapshots);
        List<Map<String, Object>> symbolAttribution = buildSymbolAttribution(trades);
        List<Map<String, Object>> symbolMiniTimelines = buildSymbolMiniTimelines(trades);
        List<Map<String, Object>> pnlTimeline = buildPnlTimeline(snapshots, trades);

        analytics.put("summary", buildPortfolioSummary(
                portfolio,
                snapshots,
                positionSummary,
                riskAttribution,
                performanceWindows,
                periodExtremes,
                symbolAttribution));
        analytics.put("positionSummary", positionSummary);
        analytics.put("riskAttribution", riskAttribution);
        analytics.put("performanceWindows", performanceWindows);
        analytics.put("periodExtremes", periodExtremes);
        analytics.put("pnlTimeline", pnlTimeline);

        // Risk Metrics
        analytics.put("riskMetrics", buildRiskMetrics(portfolioId));

        UUID ownerId = parseOwnerUuid(portfolio.getOwnerId());
        analytics.put("predictionWinRate", ownerId != null
                ? Math.round(calculateWinRate(ownerId) * 100.0) / 100.0
                : 0.0);

        // Trade Stats
        analytics.put("tradeStats", buildTradeStats(trades));
        analytics.put("symbolAttribution", symbolAttribution);
        analytics.put("symbolMiniTimelines", symbolMiniTimelines);

        // Equity Curve
        analytics.put("equityCurve", buildEquityCurve(snapshots));

        return analytics;
    }

    public String buildAnalyticsExportJson(UUID portfolioId, String curveWindow, String symbolFilter) {
        Map<String, Object> analytics = applyExportFilters(getFullAnalytics(portfolioId), curveWindow, symbolFilter);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("portfolioId", portfolioId);
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("curveWindow", normalizeCurveWindow(curveWindow));
        payload.put("symbolFilter", normalizeSymbolFilter(symbolFilter));
        payload.put("analytics", analytics);
        return toJson(payload);
    }

    public byte[] buildAnalyticsExportCsv(UUID portfolioId, String curveWindow, String symbolFilter) {
        Map<String, Object> analytics = applyExportFilters(getFullAnalytics(portfolioId), curveWindow, symbolFilter);
        Map<String, Object> summary = castMap(analytics.get("summary"));
        Map<String, Object> positionSummary = castMap(analytics.get("positionSummary"));
        Map<String, Object> performanceWindows = castMap(analytics.get("performanceWindows"));
        Map<String, Object> periodExtremes = castMap(analytics.get("periodExtremes"));
        Map<String, Object> tradeStats = castMap(analytics.get("tradeStats"));
        List<Map<String, Object>> symbolAttribution = castList(analytics.get("symbolAttribution"));
        List<Map<String, Object>> riskAttribution = castList(analytics.get("riskAttribution"));

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("section", "label", "value"));
        rows.add(List.of("summary", "portfolioName", summary.get("portfolioName")));
        rows.add(List.of("summary", "visibility", summary.get("visibility")));
        rows.add(List.of("summary", "currentEquity", summary.get("currentEquity")));
        rows.add(List.of("summary", "absoluteReturn", summary.get("absoluteReturn")));
        rows.add(List.of("summary", "returnPercentage", summary.get("returnPercentage")));
        Map<String, Object> contributionSummary = castMap(summary.get("contributionSummary"));
        rows.add(List.of("summary", "realizedPnl", contributionSummary.getOrDefault("realizedPnl", 0.0)));
        rows.add(List.of("summary", "unrealizedPnl", contributionSummary.getOrDefault("unrealizedPnl", 0.0)));
        rows.add(List.of("summary", "netPnl", contributionSummary.getOrDefault("netPnl", 0.0)));
        rows.add(List.of("summary", "grossExposure", contributionSummary.getOrDefault("grossExposure", 0.0)));
        rows.add(List.of("summary", "openPositions", contributionSummary.getOrDefault("openPositions", 0)));
        rows.add(List.of("summary", "activeRiskSymbols", contributionSummary.getOrDefault("activeRiskSymbols", 0)));
        rows.add(List.of("positions", "openPositions", positionSummary.get("openPositions")));
        rows.add(List.of("positions", "grossExposure", positionSummary.get("grossExposure")));
        rows.add(List.of("positions", "realizedPnl", positionSummary.get("realizedPnl")));
        rows.add(List.of("positions", "unrealizedPnl", positionSummary.get("unrealizedPnl")));
        rows.add(List.of("context", "curveWindow", normalizeCurveWindow(curveWindow)));
        rows.add(List.of("context", "symbolFilter", Objects.requireNonNullElse(normalizeSymbolFilter(symbolFilter), "")));
        Map<String, Object> highlightSummary = castMap(summary.get("highlightSummary"));
        rows.add(List.of("summary", "topRealizedSymbol", Objects.requireNonNullElse(highlightSummary.get("topRealizedSymbol"), "")));
        rows.add(List.of("summary", "topExposureSymbol", Objects.requireNonNullElse(highlightSummary.get("topExposureSymbol"), "")));
        rows.add(List.of("summary", "sevenDayReturnPercentage", highlightSummary.getOrDefault("sevenDayReturnPercentage", 0.0)));
        rows.add(List.of("summary", "thirtyDayReturnPercentage", highlightSummary.getOrDefault("thirtyDayReturnPercentage", 0.0)));

        Map<String, Object> window7d = castMap(performanceWindows.get("7d"));
        Map<String, Object> window30d = castMap(performanceWindows.get("30d"));
        rows.add(List.of("windows", "7dReturnPercentage", window7d.getOrDefault("returnPercentage", 0.0)));
        rows.add(List.of("windows", "30dReturnPercentage", window30d.getOrDefault("returnPercentage", 0.0)));

        Map<String, Object> bestMove = castMap(periodExtremes.get("bestMove"));
        Map<String, Object> worstMove = castMap(periodExtremes.get("worstMove"));
        rows.add(List.of("extremes", "bestMoveReturnPercentage", bestMove.getOrDefault("returnPercentage", 0.0)));
        rows.add(List.of("extremes", "worstMoveReturnPercentage", worstMove.getOrDefault("returnPercentage", 0.0)));

        for (Map<String, Object> row : riskAttribution) {
            rows.add(List.of("riskAttribution", row.get("symbol") + " exposure", row.get("exposure")));
        }
        for (Map<String, Object> row : symbolAttribution) {
            rows.add(List.of("symbolAttribution", row.get("symbol") + " realizedPnl", row.get("realizedPnl")));
        }
        for (Map.Entry<String, Object> entry : castMap(tradeStats.get("symbolBreakdown")).entrySet()) {
            rows.add(List.of("symbolBreakdown", entry.getKey(), entry.getValue()));
        }

        String content = rows.stream()
                .map(row -> row.stream().map(this::escapeCsv).reduce((left, right) -> left + "," + right).orElse(""))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return content.getBytes(StandardCharsets.UTF_8);
    }

    // ==================== INTERNAL HELPERS ====================

    private double[] getSnapshotReturns(UUID portfolioId) {
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId);
        if (snapshots.size() < 2)
            return new double[0];

        double[] returns = new double[snapshots.size() - 1];
        for (int i = 1; i < snapshots.size(); i++) {
            double prev = snapshots.get(i - 1).getTotalEquity().doubleValue();
            double curr = snapshots.get(i).getTotalEquity().doubleValue();
            returns[i - 1] = prev > 0 ? (curr - prev) / prev : 0;
        }
        return returns;
    }

    private Map<String, Object> buildPortfolioSummary(
            Portfolio portfolio,
            List<PortfolioSnapshot> snapshots,
            Map<String, Object> positionSummary,
            List<Map<String, Object>> riskAttribution,
            Map<String, Object> performanceWindows,
            Map<String, Object> periodExtremes,
            List<Map<String, Object>> symbolAttribution) {
        double fallbackEquity = portfolio.getBalance() != null ? portfolio.getBalance().doubleValue() : 0.0;
        double startEquity = snapshots.isEmpty() ? fallbackEquity : snapshots.get(0).getTotalEquity().doubleValue();
        double currentEquity = snapshots.isEmpty() ? fallbackEquity : snapshots.get(snapshots.size() - 1).getTotalEquity().doubleValue();
        double peakEquity = snapshots.stream()
                .map(PortfolioSnapshot::getTotalEquity)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(currentEquity);
        double troughEquity = snapshots.stream()
                .map(PortfolioSnapshot::getTotalEquity)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .min()
                .orElse(currentEquity);
        double absoluteReturn = currentEquity - startEquity;
        double returnPercentage = startEquity > 0 ? (absoluteReturn / startEquity) * 100.0 : 0.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("portfolioId", portfolio.getId());
        summary.put("portfolioName", portfolio.getName());
        summary.put("visibility", portfolio.getVisibility() != null ? portfolio.getVisibility().name() : "PRIVATE");
        summary.put("startingEquity", Math.round(startEquity * 100.0) / 100.0);
        summary.put("currentEquity", Math.round(currentEquity * 100.0) / 100.0);
        summary.put("absoluteReturn", Math.round(absoluteReturn * 100.0) / 100.0);
        summary.put("returnPercentage", Math.round(returnPercentage * 100.0) / 100.0);
        summary.put("peakEquity", Math.round(peakEquity * 100.0) / 100.0);
        summary.put("troughEquity", Math.round(troughEquity * 100.0) / 100.0);
        summary.put("snapshotCount", snapshots.size());
        summary.put("firstSnapshotAt", snapshots.isEmpty() ? null : snapshots.get(0).getTimestamp().toString());
        summary.put("latestSnapshotAt", snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1).getTimestamp().toString());

        Map<String, Object> contributionSummary = new LinkedHashMap<>();
        contributionSummary.put("realizedPnl", positionSummary.getOrDefault("realizedPnl", 0.0));
        contributionSummary.put("unrealizedPnl", positionSummary.getOrDefault("unrealizedPnl", 0.0));
        contributionSummary.put("netPnl", positionSummary.getOrDefault("netPnl", 0.0));
        contributionSummary.put("grossExposure", positionSummary.getOrDefault("grossExposure", 0.0));
        contributionSummary.put("openPositions", positionSummary.getOrDefault("openPositions", 0));
        contributionSummary.put("activeRiskSymbols", riskAttribution.size());
        summary.put("contributionSummary", contributionSummary);

        Map<String, Object> highlightSummary = new LinkedHashMap<>();
        Map<String, Object> topRealized = symbolAttribution.isEmpty() ? Map.of() : symbolAttribution.get(0);
        Map<String, Object> topExposure = riskAttribution.isEmpty() ? Map.of() : riskAttribution.get(0);
        Map<String, Object> window7d = castMap(performanceWindows.get("7d"));
        Map<String, Object> window30d = castMap(performanceWindows.get("30d"));
        Map<String, Object> bestMove = castMap(periodExtremes.get("bestMove"));
        Map<String, Object> worstMove = castMap(periodExtremes.get("worstMove"));
        highlightSummary.put("topRealizedSymbol", topRealized.getOrDefault("symbol", null));
        highlightSummary.put("topRealizedPnl", topRealized.getOrDefault("realizedPnl", 0.0));
        highlightSummary.put("topRealizedTradeCount", topRealized.getOrDefault("tradeCount", 0));
        highlightSummary.put("topExposureSymbol", topExposure.getOrDefault("symbol", null));
        highlightSummary.put("topExposure", topExposure.getOrDefault("exposure", 0.0));
        highlightSummary.put("topExposureUnrealizedPnl", topExposure.getOrDefault("unrealizedPnl", 0.0));
        highlightSummary.put("topExposureShare", topExposure.getOrDefault("exposureShare", 0.0));
        highlightSummary.put("sevenDayReturnPercentage", window7d.getOrDefault("returnPercentage", 0.0));
        highlightSummary.put("thirtyDayReturnPercentage", window30d.getOrDefault("returnPercentage", 0.0));
        highlightSummary.put("bestMoveReturnPercentage", bestMove.getOrDefault("returnPercentage", 0.0));
        highlightSummary.put("worstMoveReturnPercentage", worstMove.getOrDefault("returnPercentage", 0.0));
        summary.put("highlightSummary", highlightSummary);
        return summary;
    }

    private Map<String, Object> buildPositionSummary(Portfolio portfolio, List<TradeActivity> trades) {
        List<PortfolioItem> items = portfolio.getItems() != null ? portfolio.getItems() : List.of();
        Map<String, MarketInstrumentResponse> instrumentSnapshots = marketDataFacadeService.getInstrumentSnapshots(
                items.stream()
                        .map(PortfolioItem::getSymbol)
                        .filter(Objects::nonNull)
                        .toList());

        double realizedPnl = trades.stream()
                .map(TradeActivity::getRealizedPnl)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .sum();
        double unrealizedPnl = 0.0;
        double grossExposure = 0.0;
        List<Map<String, Object>> topPositions = new ArrayList<>();

        for (PortfolioItem item : items) {
            MarketInstrumentResponse snapshot = instrumentSnapshots.get(
                    item.getSymbol() != null ? item.getSymbol().toUpperCase(Locale.ROOT) : null);
            double currentPrice = snapshot != null && snapshot.getCurrentPrice() > 0.0
                    ? snapshot.getCurrentPrice()
                    : item.getAveragePrice().doubleValue();
            double quantity = item.getQuantity() != null ? item.getQuantity().doubleValue() : 0.0;
            double averagePrice = item.getAveragePrice() != null ? item.getAveragePrice().doubleValue() : 0.0;
            int leverage = item.getLeverage() != null && item.getLeverage() > 0 ? item.getLeverage() : 1;
            double pnl = "SHORT".equalsIgnoreCase(item.getSide())
                    ? (averagePrice - currentPrice) * quantity
                    : (currentPrice - averagePrice) * quantity;
            double exposure = quantity * currentPrice;

            unrealizedPnl += pnl;
            grossExposure += exposure;

            Map<String, Object> position = new LinkedHashMap<>();
            position.put("symbol", item.getSymbol());
            position.put("side", item.getSide());
            position.put("leverage", leverage);
            position.put("quantity", Math.round(quantity * 10000.0) / 10000.0);
            position.put("averagePrice", Math.round(averagePrice * 100.0) / 100.0);
            position.put("currentPrice", Math.round(currentPrice * 100.0) / 100.0);
            position.put("exposure", Math.round(exposure * 100.0) / 100.0);
            position.put("unrealizedPnl", Math.round(pnl * 100.0) / 100.0);
            topPositions.add(position);
        }

        topPositions.sort((left, right) -> Double.compare(
                Math.abs(((Number) right.get("unrealizedPnl")).doubleValue()),
                Math.abs(((Number) left.get("unrealizedPnl")).doubleValue())));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openPositions", items.size());
        summary.put("grossExposure", Math.round(grossExposure * 100.0) / 100.0);
        summary.put("realizedPnl", Math.round(realizedPnl * 100.0) / 100.0);
        summary.put("unrealizedPnl", Math.round(unrealizedPnl * 100.0) / 100.0);
        summary.put("netPnl", Math.round((realizedPnl + unrealizedPnl) * 100.0) / 100.0);
        summary.put("topPositions", topPositions.stream().limit(5).toList());
        return summary;
    }

    private List<Map<String, Object>> buildRiskAttribution(Portfolio portfolio) {
        List<PortfolioItem> items = portfolio.getItems() != null ? portfolio.getItems() : List.of();
        if (items.isEmpty()) {
            return List.of();
        }

        Map<String, MarketInstrumentResponse> instrumentSnapshots = marketDataFacadeService.getInstrumentSnapshots(
                items.stream()
                        .map(PortfolioItem::getSymbol)
                        .filter(Objects::nonNull)
                        .toList());

        List<Map<String, Object>> rawRows = new ArrayList<>();
        double grossExposure = 0.0;

        for (PortfolioItem item : items) {
            String symbol = item.getSymbol();
            MarketInstrumentResponse snapshot = instrumentSnapshots.get(symbol != null ? symbol.toUpperCase(Locale.ROOT) : null);
            double currentPrice = snapshot != null && snapshot.getCurrentPrice() > 0.0
                    ? snapshot.getCurrentPrice()
                    : item.getAveragePrice().doubleValue();
            double quantity = item.getQuantity() != null ? item.getQuantity().doubleValue() : 0.0;
            double averagePrice = item.getAveragePrice() != null ? item.getAveragePrice().doubleValue() : 0.0;
            int leverage = item.getLeverage() != null && item.getLeverage() > 0 ? item.getLeverage() : 1;
            double exposure = quantity * currentPrice;
            double unrealizedPnl = "SHORT".equalsIgnoreCase(item.getSide())
                    ? (averagePrice - currentPrice) * quantity
                    : (currentPrice - averagePrice) * quantity;
            double movePercentage = averagePrice > 0
                    ? ("SHORT".equalsIgnoreCase(item.getSide())
                            ? ((averagePrice - currentPrice) / averagePrice) * 100.0
                            : ((currentPrice - averagePrice) / averagePrice) * 100.0)
                    : 0.0;

            grossExposure += exposure;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("symbol", symbol);
            row.put("side", item.getSide());
            row.put("leverage", leverage);
            row.put("quantity", Math.round(quantity * 10000.0) / 10000.0);
            row.put("averagePrice", Math.round(averagePrice * 100.0) / 100.0);
            row.put("currentPrice", Math.round(currentPrice * 100.0) / 100.0);
            row.put("exposure", Math.round(exposure * 100.0) / 100.0);
            row.put("unrealizedPnl", Math.round(unrealizedPnl * 100.0) / 100.0);
            row.put("movePercentage", Math.round(movePercentage * 100.0) / 100.0);
            rawRows.add(row);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> rawRow : rawRows) {
            double exposure = ((Number) rawRow.get("exposure")).doubleValue();
            Map<String, Object> row = new LinkedHashMap<>(rawRow);
            double exposureShare = grossExposure > 0 ? (exposure / grossExposure) * 100.0 : 0.0;
            row.put("exposureShare", Math.round(exposureShare * 100.0) / 100.0);
            rows.add(row);
        }

        rows.sort((left, right) -> Double.compare(
                ((Number) right.get("exposure")).doubleValue(),
                ((Number) left.get("exposure")).doubleValue()));

        return rows.stream().limit(8).toList();
    }

    private Map<String, Object> buildPerformanceWindows(List<PortfolioSnapshot> snapshots) {
        Map<String, Object> windows = new LinkedHashMap<>();
        windows.put("7d", buildWindowPerformance(snapshots, LocalDateTime.now().minusDays(7)));
        windows.put("30d", buildWindowPerformance(snapshots, LocalDateTime.now().minusDays(30)));
        return windows;
    }

    private Map<String, Object> buildWindowPerformance(List<PortfolioSnapshot> snapshots, LocalDateTime startTime) {
        List<PortfolioSnapshot> inWindow = snapshots.stream()
                .filter(snapshot -> snapshot.getTimestamp() != null && !snapshot.getTimestamp().isBefore(startTime))
                .toList();

        if (inWindow.isEmpty()) {
            return Map.of(
                    "startingEquity", 0.0,
                    "endingEquity", 0.0,
                    "absoluteReturn", 0.0,
                    "returnPercentage", 0.0,
                    "snapshotCount", 0);
        }

        double startEquity = inWindow.get(0).getTotalEquity().doubleValue();
        double endEquity = inWindow.get(inWindow.size() - 1).getTotalEquity().doubleValue();
        double absoluteReturn = endEquity - startEquity;
        double returnPercentage = startEquity > 0 ? (absoluteReturn / startEquity) * 100.0 : 0.0;

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("startingEquity", Math.round(startEquity * 100.0) / 100.0);
        window.put("endingEquity", Math.round(endEquity * 100.0) / 100.0);
        window.put("absoluteReturn", Math.round(absoluteReturn * 100.0) / 100.0);
        window.put("returnPercentage", Math.round(returnPercentage * 100.0) / 100.0);
        window.put("snapshotCount", inWindow.size());
        return window;
    }

    private List<Map<String, Object>> buildSymbolAttribution(List<TradeActivity> trades) {
        Map<String, Double> realizedBySymbol = new LinkedHashMap<>();
        Map<String, Integer> tradeCountBySymbol = new LinkedHashMap<>();

        for (TradeActivity trade : trades) {
            if (trade.getSymbol() == null) {
                continue;
            }
            tradeCountBySymbol.merge(trade.getSymbol(), 1, Integer::sum);
            if (trade.getRealizedPnl() != null) {
                realizedBySymbol.merge(trade.getSymbol(), trade.getRealizedPnl().doubleValue(), Double::sum);
            } else {
                realizedBySymbol.putIfAbsent(trade.getSymbol(), 0.0);
            }
        }

        return realizedBySymbol.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("symbol", entry.getKey());
                    row.put("realizedPnl", Math.round(entry.getValue() * 100.0) / 100.0);
                    row.put("tradeCount", tradeCountBySymbol.getOrDefault(entry.getKey(), 0));
                    return row;
                })
                .sorted((left, right) -> Double.compare(
                        Math.abs(((Number) right.get("realizedPnl")).doubleValue()),
                        Math.abs(((Number) left.get("realizedPnl")).doubleValue())))
                .limit(6)
                .toList();
    }

    private List<Map<String, Object>> buildSymbolMiniTimelines(List<TradeActivity> trades) {
        Map<String, List<TradeActivity>> tradesBySymbol = new LinkedHashMap<>();
        for (TradeActivity trade : trades) {
            if (trade.getSymbol() == null) {
                continue;
            }
            tradesBySymbol.computeIfAbsent(trade.getSymbol(), ignored -> new ArrayList<>()).add(trade);
        }

        return tradesBySymbol.entrySet().stream()
                .map(entry -> {
                    List<TradeActivity> symbolTrades = entry.getValue().stream()
                            .filter(Objects::nonNull)
                            .sorted(Comparator.comparing(TradeActivity::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();

                    double runningPnl = 0.0;
                    List<Map<String, Object>> points = new ArrayList<>();
                    int realizedTradeCount = 0;

                    for (TradeActivity trade : symbolTrades) {
                        if (trade.getRealizedPnl() != null) {
                            runningPnl += trade.getRealizedPnl().doubleValue();
                            realizedTradeCount++;
                        }

                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("timestamp", trade.getTimestamp() != null ? trade.getTimestamp().toString() : null);
                        point.put("cumulativePnl", Math.round(runningPnl * 100.0) / 100.0);
                        points.add(point);
                    }

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("symbol", entry.getKey());
                    row.put("tradeCount", symbolTrades.size());
                    row.put("realizedTradeCount", realizedTradeCount);
                    row.put("finalRealizedPnl", Math.round(runningPnl * 100.0) / 100.0);
                    row.put("points", points);
                    return row;
                })
                .sorted((left, right) -> Double.compare(
                        Math.abs(((Number) right.get("finalRealizedPnl")).doubleValue()),
                        Math.abs(((Number) left.get("finalRealizedPnl")).doubleValue())))
                .limit(6)
                .toList();
    }

    private Map<String, Object> buildPeriodExtremes(List<PortfolioSnapshot> snapshots) {
        List<PortfolioSnapshot> validSnapshots = snapshots.stream()
                .filter(Objects::nonNull)
                .filter(snapshot -> snapshot.getTotalEquity() != null)
                .toList();
        if (validSnapshots.size() < 2) {
            Map<String, Object> emptyMove = new LinkedHashMap<>();
            emptyMove.put("absoluteReturn", 0.0);
            emptyMove.put("returnPercentage", 0.0);
            emptyMove.put("from", null);
            emptyMove.put("to", null);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bestMove", new LinkedHashMap<>(emptyMove));
            result.put("worstMove", new LinkedHashMap<>(emptyMove));
            return result;
        }

        Map<String, Object> best = null;
        Map<String, Object> worst = null;

        for (int i = 1; i < validSnapshots.size(); i++) {
            PortfolioSnapshot previous = validSnapshots.get(i - 1);
            PortfolioSnapshot current = validSnapshots.get(i);
            double previousEquity = previous.getTotalEquity().doubleValue();
            double currentEquity = current.getTotalEquity().doubleValue();
            double absoluteReturn = currentEquity - previousEquity;
            double returnPercentage = previousEquity > 0 ? (absoluteReturn / previousEquity) * 100.0 : 0.0;

            Map<String, Object> move = new LinkedHashMap<>();
            move.put("absoluteReturn", Math.round(absoluteReturn * 100.0) / 100.0);
            move.put("returnPercentage", Math.round(returnPercentage * 100.0) / 100.0);
            move.put("from", previous.getTimestamp() != null ? previous.getTimestamp().toString() : null);
            move.put("to", current.getTimestamp() != null ? current.getTimestamp().toString() : null);

            if (best == null || ((Number) move.get("absoluteReturn")).doubleValue() > ((Number) best.get("absoluteReturn")).doubleValue()) {
                best = move;
            }
            if (worst == null || ((Number) move.get("absoluteReturn")).doubleValue() < ((Number) worst.get("absoluteReturn")).doubleValue()) {
                worst = move;
            }
        }

        Map<String, Object> extremes = new LinkedHashMap<>();
        extremes.put("bestMove", best);
        extremes.put("worstMove", worst);
        return extremes;
    }

    private List<Map<String, Object>> buildPnlTimeline(List<PortfolioSnapshot> snapshots, List<TradeActivity> trades) {
        if (snapshots.isEmpty()) {
            return List.of();
        }

        List<TradeActivity> orderedTrades = trades.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TradeActivity::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        double startingEquity = snapshots.get(0).getTotalEquity().doubleValue();
        double realizedRunning = 0.0;
        int tradeIndex = 0;
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (PortfolioSnapshot snapshot : snapshots) {
            LocalDateTime snapshotTime = snapshot.getTimestamp();
            while (tradeIndex < orderedTrades.size()) {
                TradeActivity trade = orderedTrades.get(tradeIndex);
                if (trade.getTimestamp() == null || snapshotTime == null || trade.getTimestamp().isAfter(snapshotTime)) {
                    break;
                }
                if (trade.getRealizedPnl() != null) {
                    realizedRunning += trade.getRealizedPnl().doubleValue();
                }
                tradeIndex++;
            }

            double equity = snapshot.getTotalEquity().doubleValue();
            double netPnl = equity - startingEquity;
            double unrealizedPnl = netPnl - realizedRunning;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", snapshotTime != null ? snapshotTime.toString() : null);
            point.put("equity", Math.round(equity * 100.0) / 100.0);
            point.put("realizedPnl", Math.round(realizedRunning * 100.0) / 100.0);
            point.put("unrealizedPnl", Math.round(unrealizedPnl * 100.0) / 100.0);
            point.put("netPnl", Math.round(netPnl * 100.0) / 100.0);
            timeline.add(point);
        }

        return timeline;
    }

    private Map<String, Object> applyExportFilters(Map<String, Object> analytics, String curveWindow, String symbolFilter) {
        Map<String, Object> copy = new LinkedHashMap<>(analytics);
        String normalizedFilter = normalizeSymbolFilter(symbolFilter);
        String normalizedCurveWindow = normalizeCurveWindow(curveWindow);

        if (normalizedFilter != null) {
            copy.put("symbolAttribution", filterRowsBySymbol(castList(copy.get("symbolAttribution")), normalizedFilter));
            copy.put("riskAttribution", filterRowsBySymbol(castList(copy.get("riskAttribution")), normalizedFilter));
            copy.put("symbolMiniTimelines", filterRowsBySymbol(castList(copy.get("symbolMiniTimelines")), normalizedFilter));

            Map<String, Object> tradeStats = new LinkedHashMap<>(castMap(copy.get("tradeStats")));
            Map<String, Object> symbolBreakdown = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : castMap(tradeStats.get("symbolBreakdown")).entrySet()) {
                if (entry.getKey().toUpperCase(Locale.ROOT).contains(normalizedFilter)) {
                    symbolBreakdown.put(entry.getKey(), entry.getValue());
                }
            }
            tradeStats.put("symbolBreakdown", symbolBreakdown);
            copy.put("tradeStats", tradeStats);

            Map<String, Object> positionSummary = new LinkedHashMap<>(castMap(copy.get("positionSummary")));
            positionSummary.put("topPositions", filterRowsBySymbol(castList(positionSummary.get("topPositions")), normalizedFilter));
            copy.put("positionSummary", positionSummary);
        }

        copy.put("equityCurve", filterCurveByWindow(castList(copy.get("equityCurve")), normalizedCurveWindow));
        copy.put("pnlTimeline", filterCurveByWindow(castList(copy.get("pnlTimeline")), normalizedCurveWindow));
        return copy;
    }

    private List<Map<String, Object>> filterRowsBySymbol(List<Map<String, Object>> rows, String normalizedFilter) {
        if (normalizedFilter == null || rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> Objects.toString(row.get("symbol"), "").toUpperCase(Locale.ROOT).contains(normalizedFilter))
                .toList();
    }

    private List<Map<String, Object>> filterCurveByWindow(List<Map<String, Object>> points, String curveWindow) {
        if (points.isEmpty() || "ALL".equals(curveWindow)) {
            return points;
        }
        long days = "7D".equals(curveWindow) ? 7 : 30;
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        List<Map<String, Object>> filtered = points.stream()
                .filter(point -> {
                    Object timestamp = point.get("timestamp");
                    if (timestamp == null) {
                        return false;
                    }
                    try {
                        return !LocalDateTime.parse(String.valueOf(timestamp)).isBefore(threshold);
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .toList();
        return filtered.isEmpty() ? points : filtered;
    }

    private String normalizeCurveWindow(String curveWindow) {
        if (curveWindow == null || curveWindow.isBlank()) {
            return "ALL";
        }
        String normalized = curveWindow.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "7D", "30D", "ALL" -> normalized;
            default -> "ALL";
        };
    }

    private String normalizeSymbolFilter(String symbolFilter) {
        if (symbolFilter == null || symbolFilter.isBlank()) {
            return null;
        }
        return symbolFilter.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTradeType(String tradeType) {
        if (tradeType == null || tradeType.isBlank()) {
            return "";
        }
        return tradeType.trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String escapeCsv(Object value) {
        String raw = value == null ? "" : String.valueOf(value);
        if (raw.contains(",") || raw.contains("\"") || raw.contains("\n")) {
            return "\"" + raw.replace("\"", "\"\"") + "\"";
        }
        return raw;
    }

    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String stringValue) {
            return "\"" + stringValue
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(entry -> toJson(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()))
                    .reduce((left, right) -> left + "," + right)
                    .map(body -> "{" + body + "}")
                    .orElse("{}");
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::toJson)
                    .reduce((left, right) -> left + "," + right)
                    .map(body -> "[" + body + "]")
                    .orElse("[]");
        }
        return toJson(String.valueOf(value));
    }

    private Portfolio loadPortfolioOrThrow(UUID portfolioId) {
        return portfolioRepository.findWithItemsById(portfolioId)
                .orElseThrow(() -> ApiRequestException.notFound(
                        "analytics_portfolio_not_found",
                        "Analytics portfolio not found"));
    }

    private Map<String, Object> buildRiskMetrics(UUID portfolioId) {
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("maxDrawdown", Math.round(calculateMaxDrawdown(portfolioId) * 100.0) / 100.0);
        risk.put("sharpeRatio", Math.round(calculateSharpeRatio(portfolioId) * 100.0) / 100.0);
        risk.put("sortinoRatio", Math.round(calculateSortinoRatio(portfolioId) * 100.0) / 100.0);
        risk.put("volatility", Math.round(calculateVolatility(portfolioId) * 100.0) / 100.0);
        risk.put("profitFactor", Math.round(calculateProfitFactor(portfolioId) * 100.0) / 100.0);
        return risk;
    }

    private void ensurePortfolioExists(UUID portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw ApiRequestException.notFound(
                    "analytics_portfolio_not_found",
                    "Analytics portfolio not found");
        }
    }

    private UUID parseOwnerUuid(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(ownerId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> buildEquityCurve(List<PortfolioSnapshot> snapshots) {
        List<Map<String, Object>> curve = new ArrayList<>();
        double peak = 0;

        for (PortfolioSnapshot s : snapshots) {
            double equity = s.getTotalEquity().doubleValue();
            if (equity > peak)
                peak = equity;
            double drawdown = peak > 0 ? ((peak - equity) / peak) * 100 : 0;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("timestamp", s.getTimestamp().toString());
            point.put("equity", Math.round(equity * 100.0) / 100.0);
            point.put("drawdown", Math.round(drawdown * 100.0) / 100.0);
            point.put("peak", Math.round(peak * 100.0) / 100.0);
            curve.add(point);
        }
        return curve;
    }
}
