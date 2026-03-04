package com.finance.core.service;

import com.finance.core.domain.Notification;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.domain.event.NotificationEvent;
import com.finance.core.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceAlertService {

    private final WatchlistItemRepository watchlistItemRepository;
    private final BinanceService binanceService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Runs every 10 seconds and checks all active price alerts against live market
     * prices.
     * When a price threshold is crossed, it fires a notification and marks the
     * alert as triggered.
     */
    @Scheduled(fixedDelay = 10000)
    @SchedulerLock(name = "PriceAlertService.checkPriceAlerts", lockAtMostFor = "PT1M", lockAtLeastFor = "PT2S")
    @Transactional
    public void checkPriceAlerts() {
        List<WatchlistItem> activeAlerts = watchlistItemRepository.findAllWithActiveAlerts();
        if (activeAlerts.isEmpty())
            return;

        Map<String, Double> prices = binanceService.getPrices();
        if (prices.isEmpty())
            return;

        int triggered = 0;

        for (WatchlistItem item : activeAlerts) {
            Double currentPrice = prices.get(item.getSymbol());
            if (currentPrice == null)
                continue;

            BigDecimal current = BigDecimal.valueOf(currentPrice);

            // Check ABOVE alert
            if (item.getAlertPriceAbove() != null && !item.getAlertAboveTriggered()) {
                if (current.compareTo(item.getAlertPriceAbove()) >= 0) {
                    item.setAlertAboveTriggered(true);
                    watchlistItemRepository.save(item);

                    // Fire notification
                    eventPublisher.publishEvent(NotificationEvent.builder()
                            .receiverId(item.getWatchlist().getUserId())
                            .type(Notification.NotificationType.PRICE_ALERT)
                            .referenceId(item.getId())
                            .referenceLabel(String.format("🚀 %s hit $%s (above alert: $%s)",
                                    item.getSymbol(), current.toPlainString(),
                                    item.getAlertPriceAbove().toPlainString()))
                            .build());

                    triggered++;
                    log.info("PRICE ALERT TRIGGERED: {} above {} (current: {})",
                            item.getSymbol(), item.getAlertPriceAbove(), current);
                }
            }

            // Check BELOW alert
            if (item.getAlertPriceBelow() != null && !item.getAlertBelowTriggered()) {
                if (current.compareTo(item.getAlertPriceBelow()) <= 0) {
                    item.setAlertBelowTriggered(true);
                    watchlistItemRepository.save(item);

                    eventPublisher.publishEvent(NotificationEvent.builder()
                            .receiverId(item.getWatchlist().getUserId())
                            .type(Notification.NotificationType.PRICE_ALERT)
                            .referenceId(item.getId())
                            .referenceLabel(String.format("📉 %s dropped to $%s (below alert: $%s)",
                                    item.getSymbol(), current.toPlainString(),
                                    item.getAlertPriceBelow().toPlainString()))
                            .build());

                    triggered++;
                    log.info("PRICE ALERT TRIGGERED: {} below {} (current: {})",
                            item.getSymbol(), item.getAlertPriceBelow(), current);
                }
            }
        }

        if (triggered > 0) {
            log.info("Price alert check complete: {} alerts triggered out of {} active.", triggered,
                    activeAlerts.size());
        }
    }
}
