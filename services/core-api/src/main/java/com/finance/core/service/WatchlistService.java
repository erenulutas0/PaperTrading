package com.finance.core.service;

import com.finance.core.domain.Watchlist;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.repository.UserRepository;
import com.finance.core.repository.WatchlistItemRepository;
import com.finance.core.repository.WatchlistRepository;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final MarketDataFacadeService marketDataFacadeService;
    private final UserRepository userRepository;

    /** Get all watchlists for a user */
    @Transactional(readOnly = true)
    public List<Watchlist> getUserWatchlists(UUID userId) {
        ensureUserExists(userId);
        return watchlistRepository.findByUserId(userId);
    }

    /** Get all watchlists for a user (paged) */
    @Transactional(readOnly = true)
    public Page<Watchlist> getUserWatchlists(UUID userId, Pageable pageable) {
        ensureUserExists(userId);
        return watchlistRepository.findByUserId(userId, pageable);
    }

    /** Create a new watchlist */
    public Watchlist createWatchlist(UUID userId, String name) {
        ensureUserExists(userId);
        Watchlist watchlist = Watchlist.builder()
                .userId(userId)
                .name(name != null ? name : "My Watchlist")
                .build();
        return watchlistRepository.save(watchlist);
    }

    /** Delete a watchlist (only if owned by user) */
    @Transactional
    public void deleteWatchlist(UUID watchlistId, UUID userId) {
        ensureUserExists(userId);
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("watchlist_not_found", "Watchlist not found"));
        watchlistRepository.delete(watchlist);
        log.info("Watchlist {} deleted by user {}", watchlistId, userId);
    }

    /** Add a symbol to a watchlist */
    @Transactional
    public WatchlistItem addItem(UUID watchlistId, UUID userId, String symbol,
            BigDecimal alertAbove, BigDecimal alertBelow, String notes) {
        ensureUserExists(userId);
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("watchlist_not_found", "Watchlist not found"));

        WatchlistItem item = WatchlistItem.builder()
                .watchlist(watchlist)
                .symbol(symbol.toUpperCase(Locale.ROOT))
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
    public void removeItem(UUID itemId, UUID userId) {
        ensureUserExists(userId);
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("watchlist_item_not_found", "Watchlist item not found"));
        watchlistItemRepository.delete(item);
    }

    /** Update alert prices for an item */
    @Transactional
    public WatchlistItem updateAlerts(UUID itemId, UUID userId, BigDecimal alertAbove, BigDecimal alertBelow) {
        ensureUserExists(userId);
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("watchlist_item_not_found", "Watchlist item not found"));

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
        return getEnrichedItemsPage(watchlistId, userId, Pageable.unpaged()).getContent();
    }

    /** Get enriched watchlist items with current prices (paged) */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> getEnrichedItemsPage(UUID watchlistId, UUID userId, Pageable pageable) {
        ensureUserExists(userId);
        Watchlist watchlist = watchlistRepository.findByIdAndUserId(watchlistId, userId)
                .orElseThrow(() -> ApiRequestException.notFound("watchlist_not_found", "Watchlist not found"));

        Page<WatchlistItem> itemsPage = pageable.isPaged()
                ? watchlistItemRepository.findByWatchlistIdOrderByAddedAtAsc(watchlist.getId(), pageable)
                : new PageImpl<>(watchlist.getItems());

        Map<String, com.finance.core.dto.MarketInstrumentResponse> snapshots = marketDataFacadeService.getInstrumentSnapshots(
                itemsPage.getContent().stream().map(WatchlistItem::getSymbol).toList());

        return itemsPage.map(item -> {
            com.finance.core.dto.MarketInstrumentResponse snapshot = snapshots.get(item.getSymbol());
            Double currentPrice = snapshot != null ? snapshot.getCurrentPrice() : 0.0;
            return Map.<String, Object>of(
                    "id", item.getId(),
                    "symbol", item.getSymbol(),
                    "currentPrice", currentPrice,
                    "changePercent24h", snapshot != null ? snapshot.getChangePercent24h() : 0.0,
                    "alertPriceAbove", item.getAlertPriceAbove() != null ? item.getAlertPriceAbove() : "",
                    "alertPriceBelow", item.getAlertPriceBelow() != null ? item.getAlertPriceBelow() : "",
                    "alertAboveTriggered", item.getAlertAboveTriggered(),
                    "alertBelowTriggered", item.getAlertBelowTriggered(),
                    "notes", item.getNotes() != null ? item.getNotes() : "");
        });
    }

    private void ensureUserExists(UUID userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw ApiRequestException.notFound("user_not_found", "User not found");
        }
    }
}
