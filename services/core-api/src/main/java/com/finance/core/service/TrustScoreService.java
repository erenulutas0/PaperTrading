package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.TrustScoreSnapshot;
import com.finance.core.dto.TrustScoreBreakdownResponse;
import com.finance.core.dto.TrustScoreHistoryPointResponse;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.TradeActivityRepository;
import com.finance.core.repository.TrustScoreSnapshotRepository;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;

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
    private static final double EXPERIENCE_PER_PORTFOLIO = 0.50;
    private static final double MAX_EXPERIENCE_BONUS = 10.0;
    private static final double CONFIDENCE_HALF_SATURATION = 20.0;
    private static final Duration HISTORY_STALENESS_THRESHOLD = Duration.ofMinutes(15);
    private final UserRepository userRepository;
    private final PerformanceAnalyticsService analyticsService;
    private final PortfolioRepository portfolioRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final TrustScoreSnapshotRepository trustScoreSnapshotRepository;
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
            List<TrustScoreSnapshot> snapshots = new ArrayList<>(userPage.getNumberOfElements());
            LocalDateTime capturedAt = LocalDateTime.now();

            for (AppUser user : userPage.getContent()) {
                TrustScoreBreakdownResponse breakdown = buildTrustScoreBreakdown(user.getId());
                double score = calculateTrustScore(breakdown);

                user.setTrustScore(score);
                batch.add(user);
                snapshots.add(buildSnapshot(user.getId(), score, breakdown, capturedAt));
            }

            if (!batch.isEmpty()) {
                userRepository.saveAll(batch);
                trustScoreSnapshotRepository.saveAll(snapshots);
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

        double predictionPosteriorRate = calculatePosteriorRate(
                predictionWinRate,
                resolvedPredictionCount,
                PREDICTION_PRIOR_WEIGHT);
        double tradePosteriorRate = calculatePosteriorRate(
                tradeWinRate,
                resolvedTradeCount,
                TRADE_PRIOR_WEIGHT);
        double portfolioPosteriorRate = calculatePosteriorRate(
                portfolioSignals.portfolioWinRate,
                portfolioSignals.totalPortfolioCount,
                PORTFOLIO_PRIOR_WEIGHT);

        double predictionComponent = calculatePosteriorComponent(predictionPosteriorRate, PREDICTION_MULTIPLIER);
        double tradeComponent = calculatePosteriorComponent(tradePosteriorRate, TRADE_MULTIPLIER);
        double portfolioComponent = calculatePosteriorComponent(portfolioPosteriorRate, PORTFOLIO_MULTIPLIER);

        double clampedAverageReturn = Math.max(-MAX_RETURN_ABS_PERCENT,
                Math.min(MAX_RETURN_ABS_PERCENT, portfolioSignals.averagePortfolioReturn.doubleValue()));
        double returnComponent = (clampedAverageReturn / MAX_RETURN_ABS_PERCENT) * RETURN_MULTIPLIER;
        long totalEvidenceCount = resolvedPredictionCount + resolvedTradeCount + portfolioSignals.totalPortfolioCount;
        double confidenceScore = calculateConfidenceScore(totalEvidenceCount);
        double blendedWinRate = calculateBlendedWinRate(
                predictionPosteriorRate,
                resolvedPredictionCount,
                tradePosteriorRate,
                resolvedTradeCount,
                portfolioPosteriorRate,
                portfolioSignals.totalPortfolioCount);
        double experienceMagnitude = Math.min(
                MAX_EXPERIENCE_BONUS,
                (resolvedPredictionCount * EXPERIENCE_PER_RESOLVED_POST)
                        + (resolvedTradeCount * EXPERIENCE_PER_RESOLVED_TRADE)
                        + (portfolioSignals.totalPortfolioCount * EXPERIENCE_PER_PORTFOLIO));
        double experienceAlignment = Math.max(-1.0, Math.min(1.0, (blendedWinRate - 50.0) / 50.0));
        double experienceComponent = experienceMagnitude * experienceAlignment;

        return TrustScoreBreakdownResponse.builder()
                .blendedWinRate(roundDouble(blendedWinRate))
                .predictionWinRate(roundDouble(predictionWinRate))
                .predictionPosteriorRate(roundDouble(predictionPosteriorRate))
                .resolvedPredictionCount(resolvedPredictionCount)
                .tradeWinRate(roundDouble(tradeWinRate))
                .tradePosteriorRate(roundDouble(tradePosteriorRate))
                .resolvedTradeCount(resolvedTradeCount)
                .profitablePortfolioCount(portfolioSignals.profitablePortfolioCount)
                .totalPortfolioCount(portfolioSignals.totalPortfolioCount)
                .portfolioWinRate(roundDouble(portfolioSignals.portfolioWinRate))
                .portfolioPosteriorRate(roundDouble(portfolioPosteriorRate))
                .averagePortfolioReturn(portfolioSignals.averagePortfolioReturn)
                .aggregateRealizedPnl(aggregateRealizedPnl.setScale(2, RoundingMode.HALF_UP))
                .totalEvidenceCount(totalEvidenceCount)
                .confidenceScore(roundDouble(confidenceScore))
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

    public List<TrustScoreHistoryPointResponse> buildTrustHistory(UUID userId, TrustScoreBreakdownResponse breakdown, double currentScore, int limit) {
        int safeLimit = Math.max(1, limit);
        List<TrustScoreSnapshot> recentSnapshots = trustScoreSnapshotRepository.findByUserIdOrderByCapturedAtDesc(
                userId,
                PageRequest.of(0, safeLimit));

        List<TrustScoreHistoryPointResponse> points = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        double currentWinRate = roundDouble(breakdown.getBlendedWinRate());

        if (!recentSnapshots.isEmpty()) {
            TrustScoreSnapshot latest = recentSnapshots.get(0);
            boolean latestIsFresh = Duration.between(latest.getCapturedAt(), now).abs().compareTo(HISTORY_STALENESS_THRESHOLD) <= 0;
            if (latestIsFresh) {
                points.addAll(recentSnapshots.stream()
                        .map(this::toHistoryPoint)
                        .toList());
            } else {
                points.add(TrustScoreHistoryPointResponse.builder()
                        .capturedAt(now)
                        .trustScore(roundDouble(currentScore))
                        .winRate(currentWinRate)
                        .build());
                points.addAll(recentSnapshots.stream()
                        .limit(Math.max(0, safeLimit - 1))
                        .map(this::toHistoryPoint)
                        .toList());
            }
        } else {
            points.add(TrustScoreHistoryPointResponse.builder()
                    .capturedAt(now)
                    .trustScore(roundDouble(currentScore))
                    .winRate(currentWinRate)
                    .build());
        }

        return points.stream()
                .sorted(Comparator.comparing(TrustScoreHistoryPointResponse::getCapturedAt))
                .limit(safeLimit)
                .toList();
    }

    public double calculateTrustScoreChange7d(UUID userId, double currentScore) {
        TrustScoreSnapshot baseline = findTrendBaseline(userId);
        if (baseline == null) {
            return 0.0;
        }
        return roundDouble(currentScore - baseline.getTrustScore());
    }

    public double calculateWinRateChange7d(UUID userId, TrustScoreBreakdownResponse breakdown) {
        TrustScoreSnapshot baseline = findTrendBaseline(userId);
        if (baseline == null) {
            return 0.0;
        }
        return roundDouble(breakdown.getBlendedWinRate() - baseline.getWinRate());
    }

    private double calculatePosteriorRate(double ratePercent, long sampleSize, double priorWeight) {
        double normalizedRate = Math.max(0.0, Math.min(100.0, ratePercent)) / 100.0;
        double posteriorRate = ((normalizedRate * sampleSize) + (PRIOR_WIN_RATE * priorWeight))
                / (sampleSize + priorWeight);
        return posteriorRate * 100.0;
    }

    private double calculatePosteriorComponent(double posteriorRatePercent, double multiplier) {
        double normalizedPosteriorRate = Math.max(0.0, Math.min(100.0, posteriorRatePercent)) / 100.0;
        return (normalizedPosteriorRate - PRIOR_WIN_RATE) * multiplier;
    }

    private double calculateConfidenceScore(long totalEvidenceCount) {
        if (totalEvidenceCount <= 0) {
            return 0.0;
        }
        return (totalEvidenceCount / (totalEvidenceCount + CONFIDENCE_HALF_SATURATION)) * 100.0;
    }

    private double calculateBlendedWinRate(
            double predictionPosteriorRate,
            long resolvedPredictionCount,
            double tradePosteriorRate,
            long resolvedTradeCount,
            double portfolioPosteriorRate,
            int totalPortfolioCount) {
        double predictionWeight = resolvedPredictionCount;
        double tradeWeight = resolvedTradeCount;
        double portfolioWeight = totalPortfolioCount;
        double totalWeight = predictionWeight + tradeWeight + portfolioWeight;
        if (totalWeight <= 0) {
            return BASELINE_SCORE;
        }
        return ((predictionPosteriorRate * predictionWeight)
                + (tradePosteriorRate * tradeWeight)
                + (portfolioPosteriorRate * portfolioWeight)) / totalWeight;
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

    private TrustScoreSnapshot buildSnapshot(UUID userId, double trustScore, TrustScoreBreakdownResponse breakdown, LocalDateTime capturedAt) {
        return TrustScoreSnapshot.builder()
                .userId(userId)
                .trustScore(roundDouble(trustScore))
                .winRate(roundDouble(breakdown.getBlendedWinRate()))
                .resolvedPredictionCount(breakdown.getResolvedPredictionCount())
                .resolvedTradeCount(breakdown.getResolvedTradeCount())
                .portfolioCount(breakdown.getTotalPortfolioCount())
                .capturedAt(capturedAt)
                .build();
    }

    private TrustScoreHistoryPointResponse toHistoryPoint(TrustScoreSnapshot snapshot) {
        return TrustScoreHistoryPointResponse.builder()
                .capturedAt(snapshot.getCapturedAt())
                .trustScore(snapshot.getTrustScore())
                .winRate(snapshot.getWinRate())
                .build();
    }

    private TrustScoreSnapshot findTrendBaseline(UUID userId) {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        return trustScoreSnapshotRepository
                .findTopByUserIdAndCapturedAtLessThanEqualOrderByCapturedAtDesc(userId, sevenDaysAgo)
                .or(() -> trustScoreSnapshotRepository.findByUserIdOrderByCapturedAtDesc(userId, PageRequest.of(0, 1)).stream().findFirst())
                .orElse(null);
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
