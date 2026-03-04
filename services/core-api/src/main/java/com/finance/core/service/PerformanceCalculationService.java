package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.repository.PortfolioSnapshotRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceCalculationService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final BinanceService binanceService;

    public static final BigDecimal INITIAL_CAPITAL = BigDecimal.valueOf(100000);

    @Data
    @Builder
    public static class PerformanceMetrics {
        private BigDecimal currentEquity;
        private BigDecimal startEquity;
        private BigDecimal profitLoss;
        private BigDecimal returnPercentage;
    }

    public BigDecimal calculateReturn(Portfolio portfolio, String period) {
        LocalDateTime startTime = getStartTimeForPeriod(period);
        Map<String, Double> prices = binanceService.getPrices();
        return calculateMetrics(portfolio, startTime, period, prices).getReturnPercentage();
    }

    public PerformanceMetrics calculateMetrics(Portfolio portfolio, LocalDateTime startTime, String period,
            Map<String, Double> prices) {
        BigDecimal currentEquity = PerformanceTrackingService.calculateTotalEquity(portfolio, prices);
        BigDecimal investedMargin = PerformanceTrackingService.calculateInvestedMargin(portfolio);

        BigDecimal baseEquity;
        if ("ALL".equalsIgnoreCase(period)) {
            baseEquity = INITIAL_CAPITAL;
        } else {
            baseEquity = snapshotRepository
                    .findFirstByPortfolioIdAndTimestampLessThanEqualOrderByTimestampDesc(portfolio.getId(), startTime)
                    .map(PortfolioSnapshot::getTotalEquity)
                    .orElse(INITIAL_CAPITAL);
        }

        BigDecimal profitLoss = currentEquity.subtract(baseEquity);

        // ROE CALCULATION (Denominator Selection)
        // If there are active positions, we MUST use the Invested Margin to show the
        // 'true' trading performance (ROE).
        // If no positions are active, we revert to total capital (Base Equity) to avoid
        // division by zero or 1% results.
        BigDecimal denominator;
        if (investedMargin.compareTo(BigDecimal.ZERO) > 0) {
            denominator = investedMargin;
        } else {
            denominator = baseEquity;
        }

        log.info("CALC METRICS [{}]: profitLoss={}, investedMargin={}, baseEquity={}, chosenDenominator={}",
                period, profitLoss, investedMargin, baseEquity, denominator);

        BigDecimal returnPct = BigDecimal.ZERO;
        if (denominator.compareTo(BigDecimal.ZERO) > 0) {
            returnPct = profitLoss
                    .divide(denominator, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return PerformanceMetrics.builder()
                .currentEquity(currentEquity)
                .startEquity(baseEquity)
                .profitLoss(profitLoss)
                .returnPercentage(returnPct)
                .build();
    }

    public BigDecimal calculateReturnWithPrices(Portfolio portfolio, LocalDateTime startTime, String period,
            Map<String, Double> prices) {
        return calculateMetrics(portfolio, startTime, period, prices).getReturnPercentage();
    }

    public BigDecimal calculateCurrentEquity(Portfolio portfolio) {
        Map<String, Double> prices = binanceService.getPrices();
        return PerformanceTrackingService.calculateTotalEquity(portfolio, prices);
    }

    public LocalDateTime getStartTimeForPeriod(String period) {
        LocalDateTime now = LocalDateTime.now();
        if (period == null)
            period = "1D";
        switch (period.toUpperCase()) {
            case "1W":
                return now.minusWeeks(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "1M":
                return now.minusMonths(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "1Y":
                return now.minusYears(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "ALL":
                return LocalDateTime.of(2000, 1, 1, 0, 0);
            case "1D":
            default:
                return now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        }
    }
}
