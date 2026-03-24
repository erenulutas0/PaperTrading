package com.finance.core.controller;

import com.finance.core.dto.MarketType;
import com.finance.core.service.MarketDataFacadeService;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/market")
@RequiredArgsConstructor
public class MarketController {

    private static final Set<String> SUPPORTED_RANGES = Set.of("1D", "1W", "1M", "3M", "6M", "1Y", "ALL");
    private static final Set<String> SUPPORTED_INTERVALS = Set.of("1m", "15m", "30m", "1h", "4h", "1d");

    private final MarketDataFacadeService marketDataFacadeService;

    @GetMapping("/prices")
    public ResponseEntity<?> getPrices(
            @RequestParam(required = false) String market,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(marketDataFacadeService.getPrices(parseMarketType(market)));
        } catch (ApiRequestException ex) {
            return ApiErrorResponses.build(ex.status(), ex.code(), ex.getMessage(), ex.details(), request);
        }
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getSupportedInstruments(
            @RequestParam(required = false) String market,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(marketDataFacadeService.getSupportedInstruments(parseMarketType(market)));
        } catch (ApiRequestException ex) {
            return ApiErrorResponses.build(ex.status(), ex.code(), ex.getMessage(), ex.details(), request);
        }
    }

    @GetMapping("/candles")
    public ResponseEntity<?> getCandles(
            @RequestParam String symbol,
            @RequestParam(required = false) String market,
            @RequestParam(defaultValue = "1D") String range,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(required = false) String beforeOpenTime,
            @RequestParam(required = false) String limit,
            HttpServletRequest request) {
        try {
            Long parsedBeforeOpenTime = parseBeforeOpenTime(beforeOpenTime);
            Integer parsedLimit = parseLimit(limit);
            validateSymbol(symbol);
            validateRange(range);
            validateInterval(interval);
            return ResponseEntity.ok(marketDataFacadeService.getCandles(
                    parseMarketType(market),
                    symbol,
                    range,
                    interval,
                    parsedBeforeOpenTime,
                    parsedLimit));
        } catch (ApiRequestException ex) {
            return ApiErrorResponses.build(ex.status(), ex.code(), ex.getMessage(), ex.details(), request);
        }
    }

    private MarketType parseMarketType(String market) {
        try {
            return MarketType.fromNullable(market);
        } catch (IllegalArgumentException ex) {
            throw ApiRequestException.badRequest("invalid_market_type", "Invalid market type");
        }
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw ApiRequestException.badRequest("market_symbol_required", "Market symbol is required");
        }
    }

    private void validateRange(String range) {
        String normalized = range == null ? "1D" : range.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_RANGES.contains(normalized)) {
            throw ApiRequestException.badRequest("invalid_market_range", "Invalid market range");
        }
    }

    private void validateInterval(String interval) {
        String normalized = interval == null ? "1h" : interval.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_INTERVALS.contains(normalized)) {
            throw ApiRequestException.badRequest("invalid_market_interval", "Invalid market interval");
        }
    }

    private Long parseBeforeOpenTime(String rawBeforeOpenTime) {
        if (rawBeforeOpenTime == null || rawBeforeOpenTime.isBlank()) {
            return null;
        }
        final long parsed;
        try {
            parsed = Long.parseLong(rawBeforeOpenTime.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest("invalid_market_before_open_time", "Invalid market beforeOpenTime");
        }
        if (parsed <= 0) {
            throw ApiRequestException.badRequest("invalid_market_before_open_time", "Invalid market beforeOpenTime");
        }
        return parsed;
    }

    private Integer parseLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(rawLimit.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest("invalid_market_limit", "Invalid market limit");
        }
        if (parsed <= 0 || parsed > 1000) {
            throw ApiRequestException.badRequest("invalid_market_limit", "Invalid market limit");
        }
        return parsed;
    }
}
