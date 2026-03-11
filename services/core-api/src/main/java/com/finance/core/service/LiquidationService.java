package com.finance.core.service;

import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioItem;
import com.finance.core.repository.PortfolioItemRepository;
import com.finance.core.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidationService {

    private static final int LIQUIDATION_BATCH_SIZE = 250;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioItemRepository portfolioItemRepository;
    private final com.finance.core.repository.TradeActivityRepository tradeActivityRepository;
    private final BinanceService binanceService;

    @Scheduled(fixedDelay = 5000) // Run every 5 seconds
    @SchedulerLock(name = "LiquidationService.monitorLiquidations", lockAtMostFor = "PT45S", lockAtLeastFor = "PT2S")
    @Transactional
    public void monitorLiquidations() {
        Map<String, Double> prices = binanceService.getPrices();
        if (prices.isEmpty())
            return;

        int page = 0;
        Page<UUID> portfolioPage;

        do {
            portfolioPage = portfolioRepository.findAllIds(PageRequest.of(page, LIQUIDATION_BATCH_SIZE));

            for (Portfolio portfolio : loadPortfoliosWithItems(portfolioPage.getContent())) {
                boolean changed = false;
                // Iterate over items to check for liquidation
                List<PortfolioItem> items = portfolio.getItems();
                if (items == null)
                    continue;

                for (int i = 0; i < items.size(); i++) {
                    PortfolioItem item = items.get(i);
                    Double currentPriceDouble = prices.get(item.getSymbol());

                    if (currentPriceDouble != null) {
                        BigDecimal currentPrice = BigDecimal.valueOf(currentPriceDouble);
                        Integer leverage = item.getLeverage() != null ? item.getLeverage() : 1;

                        if (leverage > 1) {
                            String side = item.getSide() != null ? item.getSide().toUpperCase() : "LONG";
                            boolean isShort = "SHORT".equals(side);

                            // Calc Liquidation Price
                            BigDecimal liqPrice;
                            if (isShort) {
                                // Short: LiqPrice = EntryPrice * (1 + 1/Leverage)
                                liqPrice = item.getAveragePrice()
                                        .multiply(BigDecimal.ONE.add(
                                                BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), 8,
                                                        RoundingMode.HALF_UP)));
                            } else {
                                // Long: LiqPrice = EntryPrice * (1 - 1/Leverage)
                                liqPrice = item.getAveragePrice()
                                        .multiply(BigDecimal.ONE.subtract(
                                                BigDecimal.ONE.divide(BigDecimal.valueOf(leverage), 8,
                                                        RoundingMode.HALF_UP)));
                            }

                            // Liquidation Condition
                            boolean liquidate = isShort ? (currentPrice.compareTo(liqPrice) >= 0)
                                    : (currentPrice.compareTo(liqPrice) <= 0);

                            if (liquidate) {
                                log.info("LIQUIDATING {}: Symbol={} Portfolio={} Price={} LiqPrice={}",
                                        side, item.getSymbol(), portfolio.getName(), currentPrice, liqPrice);

                                BigDecimal marginOnEntry = item.getAveragePrice().multiply(item.getQuantity())
                                        .divide(BigDecimal.valueOf(leverage), 8, RoundingMode.HALF_UP);

                                tradeActivityRepository.save(com.finance.core.domain.TradeActivity.builder()
                                        .portfolioId(portfolio.getId())
                                        .symbol(item.getSymbol())
                                        .type("LIQUIDATION")
                                        .side(side)
                                        .quantity(item.getQuantity())
                                        .price(currentPrice)
                                        .realizedPnl(marginOnEntry.negate()) // Lost the margin
                                        .build());

                                portfolioItemRepository.delete(item);
                                items.remove(i);
                                i--;
                                changed = true;
                            }
                        }
                    }
                }

                if (changed) {
                    portfolioRepository.save(portfolio);
                }
            }

            page++;
        } while (portfolioPage.hasNext());
    }

    private List<Portfolio> loadPortfoliosWithItems(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Portfolio> loaded = new ArrayList<>(portfolioRepository.findByIdIn(ids));
        Map<UUID, Integer> order = new java.util.HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            order.put(ids.get(i), i);
        }
        loaded.sort(Comparator.comparingInt(p -> order.getOrDefault(p.getId(), Integer.MAX_VALUE)));
        return loaded;
    }
}
