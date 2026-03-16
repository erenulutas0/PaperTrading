package com.finance.core.controller;

import com.finance.core.domain.Watchlist;
import com.finance.core.domain.WatchlistAlertDirection;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.dto.WatchlistAlertEventResponse;
import com.finance.core.service.WatchlistAlertHistoryService;
import com.finance.core.service.WatchlistService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/watchlists")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;
    private final WatchlistAlertHistoryService watchlistAlertHistoryService;

    /** Get all watchlists for the current user */
    @GetMapping
    public ResponseEntity<List<Watchlist>> getUserWatchlists(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(watchlistService.getUserWatchlists(userId));
    }

    /** Create a new watchlist */
    @PostMapping
    public ResponseEntity<Watchlist> createWatchlist(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) CreateWatchlistRequest request) {
        String name = request != null ? request.getName() : null;
        return ResponseEntity.ok(watchlistService.createWatchlist(userId, name));
    }

    /** Delete a watchlist */
    @DeleteMapping("/{watchlistId}")
    public ResponseEntity<?> deleteWatchlist(
            @PathVariable UUID watchlistId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            watchlistService.deleteWatchlist(watchlistId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_delete_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Add a symbol to a watchlist (with optional price alerts) */
    @PostMapping("/{watchlistId}/items")
    public ResponseEntity<?> addItem(
            @PathVariable UUID watchlistId,
            @CurrentUserId UUID userId,
            @RequestBody AddItemRequest request,
            HttpServletRequest httpRequest) {
        try {
            WatchlistItem item = watchlistService.addItem(
                    watchlistId, userId,
                    request.getSymbol(),
                    request.getAlertPriceAbove(),
                    request.getAlertPriceBelow(),
                    request.getNotes());
            return ResponseEntity.ok(item);
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_add_item_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Remove an item from a watchlist */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> removeItem(@PathVariable UUID itemId) {
        watchlistService.removeItem(itemId);
        return ResponseEntity.ok().build();
    }

    /** Update the price alerts on a watchlist item */
    @PutMapping("/items/{itemId}/alerts")
    public ResponseEntity<?> updateAlerts(
            @PathVariable UUID itemId,
            @RequestBody UpdateAlertsRequest request,
            HttpServletRequest httpRequest) {
        try {
            WatchlistItem updated = watchlistService.updateAlerts(itemId, request.getAlertPriceAbove(),
                    request.getAlertPriceBelow());
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_update_alerts_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Get enriched watchlist items with live prices */
    @GetMapping("/{watchlistId}/items")
    public ResponseEntity<?> getEnrichedItems(
            @PathVariable UUID watchlistId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            List<Map<String, Object>> items = watchlistService.getEnrichedItems(watchlistId, userId);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_items_failed", e.getMessage(), null, httpRequest);
        }
    }

    @GetMapping("/items/{itemId}/alert-history")
    public ResponseEntity<Page<WatchlistAlertEventResponse>> getAlertHistory(
            @PathVariable UUID itemId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) WatchlistAlertDirection direction) {
        Pageable effectivePageable = pageable;
        if (limit != null && limit > 0) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), limit, pageable.getSort());
        }
        return ResponseEntity.ok(watchlistAlertHistoryService.getRecentHistoryPage(itemId, userId, effectivePageable, days, direction));
    }

    @GetMapping("/items/{itemId}/alert-history/export")
    public ResponseEntity<byte[]> exportAlertHistory(
            @PathVariable UUID itemId,
            @CurrentUserId UUID userId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) WatchlistAlertDirection direction) {
        byte[] csv = watchlistAlertHistoryService.exportHistoryCsv(itemId, userId, days, direction);
        String filename = direction == null
                ? "alert-history.csv"
                : "alert-history-" + direction.name().toLowerCase() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @Data
    static class CreateWatchlistRequest {
        private String name;
    }

    @Data
    static class AddItemRequest {
        private String symbol;
        private BigDecimal alertPriceAbove;
        private BigDecimal alertPriceBelow;
        private String notes;
    }

    @Data
    static class UpdateAlertsRequest {
        private BigDecimal alertPriceAbove;
        private BigDecimal alertPriceBelow;
    }
}
