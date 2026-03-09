package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.domain.PortfolioSnapshot;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceTrackingService {

    private static final int SNAPSHOT_BATCH_SIZE = 250;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioSnapshotRepository snapshotRepository;
    private final BinanceService binanceService;

    @Scheduled(fixedDelay = 10000) // Snapshot every 10 seconds
    @SchedulerLock(name = "PerformanceTrackingService.captureSnapshots", lockAtMostFor = "PT1M", lockAtLeastFor = "PT2S")
    @Transactional
    public void captureSnapshots() {
        log.info("Capturing portfolio snapshots...");
        Map<String, Double> prices = binanceService.getPrices();

        if (prices.isEmpty()) {
            log.warn("Skipping snapshots: No market prices available.");
            return;
        }

        long processed = 0;
        int page = 0;
        Page<Portfolio> portfolioPage;

        do {
            portfolioPage = portfolioRepository.findAllBy(PageRequest.of(page, SNAPSHOT_BATCH_SIZE));
            for (Portfolio portfolio : portfolioPage.getContent()) {
                BigDecimal equity = calculateTotalEquity(portfolio, prices);
                snapshotRepository.save(PortfolioSnapshot.builder()
                        .portfolioId(portfolio.getId())
                        .totalEquity(equity)
                        .build());
            }
            processed += portfolioPage.getNumberOfElements();
            page++;
        } while (portfolioPage.hasNext());

        log.info("Captured snapshots for {} portfolios.", processed);
    }

    public static BigDecimal calculateTotalEquity(Portfolio portfolio, Map<String, Double> prices) {
        BigDecimal balance = (portfolio.getBalance() != null) ? portfolio.getBalance() : BigDecimal.ZERO;
        BigDecimal unrealizedPL = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;

        if (portfolio.getItems() != null) {
            for (PortfolioItem item : portfolio.getItems()) {
                BigDecimal avgPrice = item.getAveragePrice();
                BigDecimal qty = item.getQuantity();

                BigDecimal itemValue = avgPrice.multiply(qty);
                BigDecimal margin = itemValue.divide(
                        BigDecimal.valueOf(item.getLeverage() != null ? item.getLeverage() : 1), 10,
                        java.math.RoundingMode.HALF_UP);
                totalMargin = totalMargin.add(margin);

                Double currentPrice = prices.get(item.getSymbol());
                BigDecimal currentPriceBD = currentPrice != null
                        ? BigDecimal.valueOf(currentPrice)
                        : avgPrice;

                BigDecimal pl;
                if ("SHORT".equals(item.getSide())) {
                    pl = avgPrice.subtract(currentPriceBD).multiply(qty);
                } else {
                    pl = currentPriceBD.subtract(avgPrice).multiply(qty);
                }
                unrealizedPL = unrealizedPL.add(pl);
            }
        }
        return balance.add(totalMargin).add(unrealizedPL);
    }

    public static BigDecimal calculateInvestedMargin(Portfolio portfolio) {
        BigDecimal totalMargin = BigDecimal.ZERO;
        if (portfolio.getItems() != null) {
            for (PortfolioItem item : portfolio.getItems()) {
                BigDecimal avgPrice = item.getAveragePrice();
                BigDecimal qty = item.getQuantity();
                BigDecimal leverage = BigDecimal.valueOf(item.getLeverage() != null ? item.getLeverage() : 1);

                BigDecimal margin = avgPrice.multiply(qty).divide(leverage, 10, java.math.RoundingMode.HALF_UP);
                totalMargin = totalMargin.add(margin);
            }
        }
        return totalMargin;
    }
}
