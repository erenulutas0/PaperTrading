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
import com.finance.core.web.PageableRequestParser;
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
import java.util.Locale;
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
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = resolveWatchlistPageable(pageable, page, size);
        try {
            return ResponseEntity.ok(watchlistService.getUserWatchlists(userId, effectivePageable));
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
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 100) Pageable pageable,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = resolveWatchlistPageable(pageable, page, size);
        try {
            Page<Map<String, Object>> items = watchlistService.getEnrichedItemsPage(watchlistId, userId, effectivePageable);
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
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String days,
            @RequestParam(required = false) String direction,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = resolveAlertHistoryPageable(pageable, page, size, limit);
        WatchlistAlertDirection parsedDirection = parseAlertHistoryDirection(direction);
        Integer validatedDays = validateAlertHistoryDays(days);
        try {
            return ResponseEntity.ok(watchlistAlertHistoryService.getRecentHistoryPage(itemId, userId, effectivePageable, validatedDays, parsedDirection));
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
            @RequestParam(required = false) String days,
            @RequestParam(required = false) String direction,
            HttpServletRequest httpRequest) {
        WatchlistAlertDirection parsedDirection = parseAlertHistoryDirection(direction);
        Integer validatedDays = validateAlertHistoryDays(days);
        try {
            byte[] csv = watchlistAlertHistoryService.exportHistoryCsv(itemId, userId, validatedDays, parsedDirection);
            String filename = parsedDirection == null
                    ? "alert-history.csv"
                    : "alert-history-" + parsedDirection.name().toLowerCase(Locale.ROOT) + ".csv";
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

    private Pageable resolveWatchlistPageable(Pageable pageable, String rawPage, String rawSize) {
        return PageableRequestParser.resolvePageable(
                pageable,
                rawPage,
                rawSize,
                "invalid_watchlist_page",
                "Invalid watchlist page",
                "invalid_watchlist_size",
                "Invalid watchlist size");
    }

    private Pageable resolveAlertHistoryPageable(Pageable pageable, String rawPage, String rawSize, String rawLimit) {
        Pageable resolvedPageable = PageableRequestParser.resolvePageable(
                pageable,
                rawPage,
                rawSize,
                "invalid_watchlist_alert_history_page",
                "Invalid watchlist alert history page",
                "invalid_watchlist_alert_history_size",
                "Invalid watchlist alert history size",
                50);
        Integer limit = parsePositiveInteger(
                rawLimit,
                "invalid_watchlist_alert_history_limit",
                "Watchlist alert history limit must be between 1 and 50");
        if (limit == null) {
            return resolvedPageable;
        }
        if (limit < 1 || limit > 50) {
            throw ApiRequestException.badRequest(
                    "invalid_watchlist_alert_history_limit",
                    "Watchlist alert history limit must be between 1 and 50");
        }
        return PageRequest.of(resolvedPageable.getPageNumber(), limit, resolvedPageable.getSort());
    }

    private Integer validateAlertHistoryDays(String rawDays) {
        Integer days = parsePositiveInteger(
                rawDays,
                "invalid_watchlist_alert_history_days",
                "Watchlist alert history days must be between 1 and 365");
        if (days == null) {
            return null;
        }
        if (days < 1 || days > 365) {
            throw ApiRequestException.badRequest(
                    "invalid_watchlist_alert_history_days",
                    "Watchlist alert history days must be between 1 and 365");
        }
        return days;
    }

    private Integer parsePositiveInteger(String rawValue, String code, String message) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest(code, message);
        }
    }

    private WatchlistAlertDirection parseAlertHistoryDirection(String rawDirection) {
        if (rawDirection == null || rawDirection.isBlank()) {
            return null;
        }
        try {
            return WatchlistAlertDirection.valueOf(rawDirection.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiRequestException.badRequest(
                    "invalid_watchlist_alert_history_direction",
                    "Watchlist alert history direction must be ABOVE or BELOW");
        }
    }
}
