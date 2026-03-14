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
import java.nio.charset.StandardCharsets;
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
    public List<WatchlistAlertEventResponse> getRecentHistory(
            UUID itemId,
            UUID userId,
            int limit,
            Integer days,
            WatchlistAlertDirection direction) {
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)
                .orElseThrow(() -> new RuntimeException("Watchlist item not found"));

        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<WatchlistAlertEvent> events = findEvents(item.getId(), safeLimit, days, direction);
        return toResponses(item.getId(), events);
    }

    @Transactional(readOnly = true)
    public byte[] exportHistoryCsv(
            UUID itemId,
            UUID userId,
            Integer days,
            WatchlistAlertDirection direction) {
        WatchlistItem item = watchlistItemRepository.findByIdAndWatchlistUserId(itemId, userId)
                .orElseThrow(() -> new RuntimeException("Watchlist item not found"));

        List<WatchlistAlertEvent> events = findEvents(item.getId(), 1000, days, direction);
        StringBuilder csv = new StringBuilder();
        csv.append("symbol,direction,thresholdPrice,triggeredPrice,message,triggeredAt\n");
        events.forEach(event -> csv
                .append(escapeCsv(event.getSymbol())).append(',')
                .append(escapeCsv(event.getDirection().name())).append(',')
                .append(escapeCsv(event.getThresholdPrice())).append(',')
                .append(escapeCsv(event.getTriggeredPrice())).append(',')
                .append(escapeCsv(event.getMessage())).append(',')
                .append(escapeCsv(event.getTriggeredAt())).append('\n'));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<WatchlistAlertEvent> findEvents(
            UUID itemId,
            int limit,
            Integer days,
            WatchlistAlertDirection direction) {
        Integer safeDays = normalizeDays(days);
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        if (direction != null && safeDays != null) {
            return watchlistAlertEventRepository.findByWatchlistItemIdAndDirectionAndTriggeredAtGreaterThanEqualOrderByTriggeredAtDesc(
                    itemId,
                    direction,
                    LocalDateTime.now().minusDays(safeDays),
                    PageRequest.of(0, safeLimit));
        }
        if (direction != null) {
            return watchlistAlertEventRepository.findByWatchlistItemIdAndDirectionOrderByTriggeredAtDesc(
                    itemId,
                    direction,
                    PageRequest.of(0, safeLimit));
        }
        if (safeDays != null) {
            return watchlistAlertEventRepository.findByWatchlistItemIdAndTriggeredAtGreaterThanEqualOrderByTriggeredAtDesc(
                    itemId,
                    LocalDateTime.now().minusDays(safeDays),
                    PageRequest.of(0, safeLimit));
        }
        return watchlistAlertEventRepository.findByWatchlistItemIdOrderByTriggeredAtDesc(itemId, PageRequest.of(0, safeLimit));
    }

    private List<WatchlistAlertEventResponse> toResponses(UUID itemId, List<WatchlistAlertEvent> events) {
        return events.stream()
                .map(event -> WatchlistAlertEventResponse.builder()
                        .id(event.getId())
                        .watchlistItemId(itemId)
                        .symbol(event.getSymbol())
                        .direction(event.getDirection())
                        .thresholdPrice(event.getThresholdPrice())
                        .triggeredPrice(event.getTriggeredPrice())
                        .message(event.getMessage())
                        .triggeredAt(event.getTriggeredAt())
                        .build())
                .toList();
    }

    private String escapeCsv(Object value) {
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }

    private Integer normalizeDays(Integer days) {
        if (days == null || days <= 0) {
            return null;
        }
        return Math.min(days, 365);
    }
}
