package com.finance.core.service;

import com.finance.core.domain.Watchlist;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.repository.WatchlistItemRepository;
import com.finance.core.repository.WatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final BinanceService binanceService;

    /** Get all watchlists for a user */
    @Transactional(readOnly = true)
    public List<Watchlist> getUserWatchlists(UUID userId) {
        return watchlistRepository.findByUserId(userId);
    }

    /** Create a new watchlist */
    public Watchlist createWatchlist(UUID userId, String name) {
        Watchlist watchlist = Watchlist.builder()
                .userId(userId)
                .name(name != null ? name : "My Watchlist")
                .build();
        return watchlistRepository.save(watchlist);
    }

    /** Delete a watchlist (only if owned by user) */
    @Transactional
    public void deleteWatchlist(UUID watchlistId, UUID userId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new RuntimeException("Watchlist not found or not owned by user"));
        watchlistRepository.delete(watchlist);
        log.info("Watchlist {} deleted by user {}", watchlistId, userId);
    }

    /** Add a symbol to a watchlist */
    @Transactional
    public WatchlistItem addItem(UUID watchlistId, UUID userId, String symbol,
            BigDecimal alertAbove, BigDecimal alertBelow, String notes) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new RuntimeException("Watchlist not found or not owned by user"));

        WatchlistItem item = WatchlistItem.builder()
                .watchlist(watchlist)
                .symbol(symbol.toUpperCase())
                .alertPriceAbove(alertAbove)
                .alertPriceBelow(alertBelow)
                .notes(notes)
                .build();

        WatchlistItem saved = watchlistItemRepository.save(item);
        log.info("Added {} to watchlist {} (alerts: above={}, below={})", symbol, watchlistId, alertAbove, alertBelow);
        return saved;
    }

    /** Remove an item from a watchlist */
    @Transactional
    public void removeItem(UUID itemId) {
        watchlistItemRepository.deleteById(itemId);
    }

    /** Update alert prices for an item */
    @Transactional
    public WatchlistItem updateAlerts(UUID itemId, BigDecimal alertAbove, BigDecimal alertBelow) {
        WatchlistItem item = watchlistItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Watchlist item not found"));

        item.setAlertPriceAbove(alertAbove);
        item.setAlertPriceBelow(alertBelow);
        // Reset triggered flags when alerts are updated
        item.setAlertAboveTriggered(false);
        item.setAlertBelowTriggered(false);

        return watchlistItemRepository.save(item);
    }

    /** Get enriched watchlist items with current prices */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getEnrichedItems(UUID watchlistId, UUID userId) {
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> new RuntimeException("Watchlist not found"));

        Map<String, Double> prices = binanceService.getPrices();
        Map<String, Double> dailyChanges = binanceService.getSupportedInstruments().stream()
                .collect(java.util.stream.Collectors.toMap(
                        instrument -> instrument.getSymbol(),
                        instrument -> instrument.getChangePercent24h()));

        return watchlist.getItems().stream().map(item -> {
            Double currentPrice = prices.getOrDefault(item.getSymbol(), 0.0);
            return Map.<String, Object>of(
                    "id", item.getId(),
                    "symbol", item.getSymbol(),
                    "currentPrice", currentPrice,
                    "changePercent24h", dailyChanges.getOrDefault(item.getSymbol(), 0.0),
                    "alertPriceAbove", item.getAlertPriceAbove() != null ? item.getAlertPriceAbove() : "",
                    "alertPriceBelow", item.getAlertPriceBelow() != null ? item.getAlertPriceBelow() : "",
                    "alertAboveTriggered", item.getAlertAboveTriggered(),
                    "alertBelowTriggered", item.getAlertBelowTriggered(),
                    "notes", item.getNotes() != null ? item.getNotes() : "");
        }).toList();
    }
}
