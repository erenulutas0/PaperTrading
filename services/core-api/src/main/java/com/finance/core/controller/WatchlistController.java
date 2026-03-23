package com.finance.core.controller;

import com.finance.core.domain.Watchlist;
import com.finance.core.domain.WatchlistAlertDirection;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.dto.WatchlistAlertEventResponse;
import com.finance.core.service.WatchlistAlertHistoryService;
import com.finance.core.service.WatchlistService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
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
    public ResponseEntity<?> getUserWatchlists(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(watchlistService.getUserWatchlists(userId, pageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlists_read_failed", "Failed to load watchlists", null, httpRequest);
        }
    }

    /** Create a new watchlist */
    @PostMapping
    public ResponseEntity<?> createWatchlist(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) CreateWatchlistRequest request,
            HttpServletRequest httpRequest) {
        String name = request != null ? request.getName() : null;
        try {
            return ResponseEntity.ok(watchlistService.createWatchlist(userId, name));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_create_failed", "Failed to create watchlist", null, httpRequest);
        }
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_delete_failed", "Failed to delete watchlist", null, httpRequest);
        }
    }

    /** Add a symbol to a watchlist (with optional price alerts) */
    @PostMapping("/{watchlistId}/items")
    public ResponseEntity<?> addItem(
            @PathVariable UUID watchlistId,
            @CurrentUserId UUID userId,
            @RequestBody AddItemRequest request,
            HttpServletRequest httpRequest) {
        if (request == null || request.getSymbol() == null || request.getSymbol().isBlank()) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_symbol_required", "Symbol is required", null, httpRequest);
        }
        try {
            WatchlistItem item = watchlistService.addItem(
                    watchlistId, userId,
                    request.getSymbol(),
                    request.getAlertPriceAbove(),
                    request.getAlertPriceBelow(),
                    request.getNotes());
            return ResponseEntity.ok(item);
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_add_item_failed", "Failed to add watchlist item", null, httpRequest);
        }
    }

    /** Remove an item from a watchlist */
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<?> removeItem(
            @PathVariable UUID itemId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            watchlistService.removeItem(itemId, userId);
            return ResponseEntity.ok().build();
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_remove_item_failed", "Failed to remove watchlist item", null, httpRequest);
        }
    }

    /** Update the price alerts on a watchlist item */
    @PutMapping("/items/{itemId}/alerts")
    public ResponseEntity<?> updateAlerts(
            @PathVariable UUID itemId,
            @CurrentUserId UUID userId,
            @RequestBody UpdateAlertsRequest request,
            HttpServletRequest httpRequest) {
        try {
            WatchlistItem updated = watchlistService.updateAlerts(itemId, userId, request.getAlertPriceAbove(),
                    request.getAlertPriceBelow());
            return ResponseEntity.ok(updated);
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_update_alerts_failed", "Failed to update watchlist alerts", null, httpRequest);
        }
    }

    /** Get enriched watchlist items with live prices */
    @GetMapping("/{watchlistId}/items")
    public ResponseEntity<?> getEnrichedItems(
            @PathVariable UUID watchlistId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 100) Pageable pageable,
            HttpServletRequest httpRequest) {
        try {
            Page<Map<String, Object>> items = watchlistService.getEnrichedItemsPage(watchlistId, userId, pageable);
            return ResponseEntity.ok(items);
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_items_failed", "Failed to load watchlist items", null, httpRequest);
        }
    }

    @GetMapping("/items/{itemId}/alert-history")
    public ResponseEntity<?> getAlertHistory(
            @PathVariable UUID itemId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) WatchlistAlertDirection direction,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = pageable;
        if (limit != null && limit > 0) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), limit, pageable.getSort());
        }
        try {
            return ResponseEntity.ok(watchlistAlertHistoryService.getRecentHistoryPage(itemId, userId, effectivePageable, days, direction));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_alert_history_failed", "Failed to load watchlist alert history", null, httpRequest);
        }
    }

    @GetMapping("/items/{itemId}/alert-history/export")
    public ResponseEntity<?> exportAlertHistory(
            @PathVariable UUID itemId,
            @CurrentUserId UUID userId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) WatchlistAlertDirection direction,
            HttpServletRequest httpRequest) {
        try {
            byte[] csv = watchlistAlertHistoryService.exportHistoryCsv(itemId, userId, days, direction);
            String filename = direction == null
                    ? "alert-history.csv"
                    : "alert-history-" + direction.name().toLowerCase(java.util.Locale.ROOT) + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "watchlist_alert_history_export_failed", "Failed to export watchlist alert history", null, httpRequest);
        }
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
