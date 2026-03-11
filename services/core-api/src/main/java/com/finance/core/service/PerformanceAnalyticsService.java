package com.finance.core.service;

import com.finance.core.domain.AnalysisPost;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.domain.TradeActivity;
import com.finance.core.repository.AnalysisPostRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
import com.finance.core.repository.TradeActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PerformanceAnalyticsService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final AnalysisPostRepository analysisPostRepository;
    private final TradeActivityRepository tradeActivityRepository;

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
        List<TradeActivity> trades = tradeActivityRepository.findByPortfolioIdOrderByTimestampDesc(portfolioId);

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
            // Type counts
            if (trade.getType() != null && trade.getType().contains("BUY"))
                buyCount++;
            else if (trade.getType() != null && trade.getType().contains("SELL"))
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
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByPortfolioIdOrderByTimestampAsc(portfolioId);
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

    // ==================== FULL DASHBOARD ====================

    /**
     * Aggregate all analytics into a single dashboard response.
     */
    public Map<String, Object> getFullAnalytics(UUID portfolioId, UUID userId) {
        Map<String, Object> analytics = new LinkedHashMap<>();

        // Risk Metrics
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("maxDrawdown", Math.round(calculateMaxDrawdown(portfolioId) * 100.0) / 100.0);
        risk.put("sharpeRatio", Math.round(calculateSharpeRatio(portfolioId) * 100.0) / 100.0);
        risk.put("sortinoRatio", Math.round(calculateSortinoRatio(portfolioId) * 100.0) / 100.0);
        risk.put("volatility", Math.round(calculateVolatility(portfolioId) * 100.0) / 100.0);
        risk.put("profitFactor", Math.round(calculateProfitFactor(portfolioId) * 100.0) / 100.0);
        analytics.put("riskMetrics", risk);

        // Win Rate (from analysis posts)
        analytics.put("predictionWinRate", Math.round(calculateWinRate(userId) * 100.0) / 100.0);

        // Trade Stats
        analytics.put("tradeStats", getTradeStats(portfolioId));

        // Equity Curve
        analytics.put("equityCurve", getEquityCurve(portfolioId));

        return analytics;
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
}
