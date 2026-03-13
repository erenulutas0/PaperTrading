package com.finance.core.service;

import com.finance.core.domain.WatchlistAlertDirection;
import com.finance.core.domain.WatchlistAlertEvent;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.dto.WatchlistAlertEventResponse;
import com.finance.core.repository.WatchlistAlertEventRepository;
import com.finance.core.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchlistAlertHistoryService {

    private final WatchlistAlertEventRepository watchlistAlertEventRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    @Transactional
    public void recordEvent(
            WatchlistItem item,
            WatchlistAlertDirection direction,
            BigDecimal thresholdPrice,
            BigDecimal triggeredPrice,
            String message) {
        watchlistAlertEventRepository.save(WatchlistAlertEvent.builder()
                .watchlistItem(item)
                .userId(item.getWatchlist().getUserId())
                .symbol(item.getSymbol())
                .direction(direction)
                .thresholdPrice(thresholdPrice)
                .triggeredPrice(triggeredPrice)
                .message(message)
                .build());
    }

    @Transactional(readOnly = true)
    public List<WatchlistAlertEventResponse> getRecentHistory(UUID itemId, UUID userId, int limit, Integer days) {
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)
                .orElseThrow(() -> new RuntimeException("Watchlist item not found"));

        int safeLimit = Math.max(1, Math.min(limit, 50));
        Integer safeDays = normalizeDays(days);
        List<WatchlistAlertEvent> events = safeDays == null
                ? watchlistAlertEventRepository.findByWatchlistItemIdOrderByTriggeredAtDesc(item.getId(), PageRequest.of(0, safeLimit))
                : watchlistAlertEventRepository.findByWatchlistItemIdAndTriggeredAtGreaterThanEqualOrderByTriggeredAtDesc(
                        item.getId(),
                        LocalDateTime.now().minusDays(safeDays),
                        PageRequest.of(0, safeLimit));
        return events
                .stream()
                .map(event -> WatchlistAlertEventResponse.builder()
                        .id(event.getId())
                        .watchlistItemId(item.getId())
                        .symbol(event.getSymbol())
                        .direction(event.getDirection())
                        .thresholdPrice(event.getThresholdPrice())
                        .triggeredPrice(event.getTriggeredPrice())
                        .message(event.getMessage())
                        .triggeredAt(event.getTriggeredAt())
                        .build())
                .toList();
    }

    private Integer normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return null;
        }
        return Math.min(days, 365);
    }
}
