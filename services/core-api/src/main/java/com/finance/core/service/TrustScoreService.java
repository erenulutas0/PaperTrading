package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.TrustScoreBreakdownResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustScoreService {

    private static final int TRUST_SCORE_BATCH_SIZE = 250;
    private static final double BASELINE_SCORE = 50.0;
    private static final double PRIOR_WIN_RATE = 0.50;
    private static final double PREDICTION_PRIOR_WEIGHT = 8.0;
    private static final double TRADE_PRIOR_WEIGHT = 10.0;
    private static final double PORTFOLIO_PRIOR_WEIGHT = 6.0;
    private static final double PREDICTION_MULTIPLIER = 22.0;
    private static final double TRADE_MULTIPLIER = 20.0;
    private static final double PORTFOLIO_MULTIPLIER = 26.0;
    private static final double MAX_RETURN_ABS_PERCENT = 20.0;
    private static final double RETURN_MULTIPLIER = 12.0;
    private static final double EXPERIENCE_PER_RESOLVED_POST = 0.35;
    private static final double EXPERIENCE_PER_RESOLVED_TRADE = 0.15;
    private static final double MAX_EXPERIENCE_BONUS = 10.0;
    private final UserRepository userRepository;
    private final PerformanceAnalyticsService analyticsService;
    private final PortfolioRepository portfolioRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final PerformanceCalculationService performanceCalculationService;
    private final BinanceService binanceService;

    /**
     * Compute trust scores for all accounts hourly.
     * Rewards consistency over luck.
     */
    @Scheduled(fixedDelay = 3600000)
    @SchedulerLock(name = "TrustScoreService.computeTrustScores", lockAtMostFor = "PT2H", lockAtLeastFor = "PT5M")
    @Transactional
    public void computeTrustScores() {
        log.info("Computing trust scores for all accounts...");
        long processed = 0;
        int page = 0;
        Page<AppUser> userPage;

        do {
            userPage = userRepository.findAll(PageRequest.of(page, TRUST_SCORE_BATCH_SIZE));
            List<AppUser> batch = new ArrayList<>(userPage.getNumberOfElements());

            for (AppUser user : userPage.getContent()) {
                TrustScoreBreakdownResponse breakdown = buildTrustScoreBreakdown(user.getId());
                double score = calculateTrustScore(breakdown);

                user.setTrustScore(score);
                batch.add(user);
            }

            if (!batch.isEmpty()) {
                userRepository.saveAll(batch);
            }
            processed += userPage.getNumberOfElements();
            page++;
        } while (userPage.hasNext());

        log.info("Trust scores computed for {} users.", processed);
    }

    public TrustScoreBreakdownResponse buildTrustScoreBreakdown(UUID userId) {
        double predictionWinRate = analyticsService.calculateWinRate(userId);
        long resolvedPredictionCount = analyticsService.countResolvedPredictions(userId);

        List<Portfolio> portfolios = portfolioRepository.findByOwnerId(userId.toString());
        List<UUID> portfolioIds = portfolios.stream().map(Portfolio::getId).toList();

        long profitableTrades = portfolioIds.isEmpty()
                ? 0
                : tradeActivityRepository.countProfitableRealizedTrades(portfolioIds);
        long losingTrades = portfolioIds.isEmpty()
                ? 0
                : tradeActivityRepository.countLosingRealizedTrades(portfolioIds);
        long resolvedTradeCount = profitableTrades + losingTrades;
        double tradeWinRate = resolvedTradeCount == 0 ? 0.0 : ((double) profitableTrades / resolvedTradeCount) * 100.0;
        BigDecimal aggregateRealizedPnl = portfolioIds.isEmpty()
                ? BigDecimal.ZERO
                : tradeActivityRepository.sumRealizedPnl(portfolioIds);

        PortfolioSignals portfolioSignals = buildPortfolioSignals(portfolios);

        double predictionComponent = calculatePosteriorComponent(
                predictionWinRate,
                resolvedPredictionCount,
                PREDICTION_PRIOR_WEIGHT,
                PREDICTION_MULTIPLIER);
        double tradeComponent = calculatePosteriorComponent(
                tradeWinRate,
                resolvedTradeCount,
                TRADE_PRIOR_WEIGHT,
                TRADE_MULTIPLIER);
        double portfolioComponent = calculatePosteriorComponent(
                portfolioSignals.portfolioWinRate,
                portfolioSignals.totalPortfolioCount,
                PORTFOLIO_PRIOR_WEIGHT,
                PORTFOLIO_MULTIPLIER);

        double clampedAverageReturn = Math.max(-MAX_RETURN_ABS_PERCENT,
                Math.min(MAX_RETURN_ABS_PERCENT, portfolioSignals.averagePortfolioReturn.doubleValue()));
        double returnComponent = (clampedAverageReturn / MAX_RETURN_ABS_PERCENT) * RETURN_MULTIPLIER;
        double experienceComponent = Math.min(
                MAX_EXPERIENCE_BONUS,
                (resolvedPredictionCount * EXPERIENCE_PER_RESOLVED_POST)
                        + (resolvedTradeCount * EXPERIENCE_PER_RESOLVED_TRADE));
        double blendedWinRate = calculateBlendedWinRate(
                predictionWinRate,
                resolvedPredictionCount,
                tradeWinRate,
                resolvedTradeCount,
                portfolioSignals.portfolioWinRate,
                portfolioSignals.totalPortfolioCount);

        return TrustScoreBreakdownResponse.builder()
                .blendedWinRate(roundDouble(blendedWinRate))
                .predictionWinRate(roundDouble(predictionWinRate))
                .resolvedPredictionCount(resolvedPredictionCount)
                .tradeWinRate(roundDouble(tradeWinRate))
                .resolvedTradeCount(resolvedTradeCount)
                .profitablePortfolioCount(portfolioSignals.profitablePortfolioCount)
                .totalPortfolioCount(portfolioSignals.totalPortfolioCount)
                .portfolioWinRate(roundDouble(portfolioSignals.portfolioWinRate))
                .averagePortfolioReturn(portfolioSignals.averagePortfolioReturn)
                .aggregateRealizedPnl(aggregateRealizedPnl.setScale(2, RoundingMode.HALF_UP))
                .predictionComponent(roundDouble(predictionComponent))
                .tradeComponent(roundDouble(tradeComponent))
                .portfolioComponent(roundDouble(portfolioComponent))
                .returnComponent(roundDouble(returnComponent))
                .experienceComponent(roundDouble(experienceComponent))
                .build();
    }

    double calculateTrustScore(TrustScoreBreakdownResponse breakdown) {
        double score = BASELINE_SCORE
                + breakdown.getPredictionComponent()
                + breakdown.getTradeComponent()
                + breakdown.getPortfolioComponent()
                + breakdown.getReturnComponent()
                + breakdown.getExperienceComponent();

        if (score > 100.0) {
            return 100.0;
        }
        if (score < 0.0) {
            return 0.0;
        }
        return Math.round(score * 100.0) / 100.0;
    }

    private double calculatePosteriorComponent(double ratePercent, long sampleSize, double priorWeight, double multiplier) {
        double normalizedRate = Math.max(0.0, Math.min(100.0, ratePercent)) / 100.0;
        double posteriorRate = ((normalizedRate * sampleSize) + (PRIOR_WIN_RATE * priorWeight))
                / (sampleSize + priorWeight);
        return (posteriorRate - PRIOR_WIN_RATE) * multiplier;
    }

    private double calculateBlendedWinRate(
            double predictionWinRate,
            long resolvedPredictionCount,
            double tradeWinRate,
            long resolvedTradeCount,
            double portfolioWinRate,
            int totalPortfolioCount) {
        double predictionWeight = resolvedPredictionCount + PREDICTION_PRIOR_WEIGHT;
        double tradeWeight = resolvedTradeCount + TRADE_PRIOR_WEIGHT;
        double portfolioWeight = totalPortfolioCount + PORTFOLIO_PRIOR_WEIGHT;
        double totalWeight = predictionWeight + tradeWeight + portfolioWeight;
        if (totalWeight <= 0) {
            return 0.0;
        }
        return ((predictionWinRate * predictionWeight)
                + (tradeWinRate * tradeWeight)
                + (portfolioWinRate * portfolioWeight)) / totalWeight;
    }

    private PortfolioSignals buildPortfolioSignals(List<Portfolio> portfolios) {
        if (portfolios == null || portfolios.isEmpty()) {
            return new PortfolioSignals(0, 0, 0.0, BigDecimal.ZERO);
        }

        Map<String, Double> prices = safePrices();
        int profitablePortfolioCount = 0;
        BigDecimal totalReturn = BigDecimal.ZERO;

        for (Portfolio portfolio : portfolios) {
            PerformanceCalculationService.PerformanceMetrics metrics = performanceCalculationService.calculateMetrics(
                    portfolio,
                    performanceCalculationService.getStartTimeForPeriod("ALL"),
                    "ALL",
                    prices);
            if (metrics.getProfitLoss().compareTo(BigDecimal.ZERO) > 0) {
                profitablePortfolioCount++;
            }
            totalReturn = totalReturn.add(metrics.getReturnPercentage());
        }

        int totalPortfolioCount = portfolios.size();
        double portfolioWinRate = totalPortfolioCount == 0
                ? 0.0
                : ((double) profitablePortfolioCount / totalPortfolioCount) * 100.0;
        BigDecimal averagePortfolioReturn = totalPortfolioCount == 0
                ? BigDecimal.ZERO
                : totalReturn.divide(BigDecimal.valueOf(totalPortfolioCount), 2, RoundingMode.HALF_UP);

        return new PortfolioSignals(
                profitablePortfolioCount,
                totalPortfolioCount,
                portfolioWinRate,
                averagePortfolioReturn);
    }

    private Map<String, Double> safePrices() {
        Map<String, Double> prices = binanceService.getPrices();
        return prices != null ? prices : Collections.emptyMap();
    }

    private double roundDouble(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record PortfolioSignals(
            int profitablePortfolioCount,
            int totalPortfolioCount,
            double portfolioWinRate,
            BigDecimal averagePortfolioReturn) {
    }
}
