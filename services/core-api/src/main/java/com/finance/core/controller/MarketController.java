package com.finance.core.controller;

import com.finance.core.dto.MarketType;
import com.finance.core.service.MarketDataFacadeService;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.ApiErrorResponses;
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
        } catch (IllegalArgumentException ex) {
            return handleMarketError(ex, request);
        }
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getSupportedInstruments(
            @RequestParam(required = false) String market,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(marketDataFacadeService.getSupportedInstruments(parseMarketType(market)));
        } catch (IllegalArgumentException ex) {
            return handleMarketError(ex, request);
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
        } catch (IllegalArgumentException ex) {
            return handleMarketError(ex, request);
        }
    }

    private MarketType parseMarketType(String market) {
        try {
            return MarketType.fromNullable(market);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid_market_type");
        }
    }

    private void validateSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("market_symbol_required");
        }
    }

    private void validateRange(String range) {
        String normalized = range == null ? "1D" : range.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_RANGES.contains(normalized)) {
            throw new IllegalArgumentException("invalid_market_range");
        }
    }

    private void validateInterval(String interval) {
        String normalized = interval == null ? "1h" : interval.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_INTERVALS.contains(normalized)) {
            throw new IllegalArgumentException("invalid_market_interval");
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
            throw new IllegalArgumentException("invalid_market_before_open_time");
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException("invalid_market_before_open_time");
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
            throw new IllegalArgumentException("invalid_market_limit");
        }
        if (parsed <= 0 || parsed > 1000) {
            throw new IllegalArgumentException("invalid_market_limit");
        }
        return parsed;
    }

    private ResponseEntity<ApiErrorResponse> handleMarketError(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        String code = resolveMarketErrorCode(ex);
        return switch (code) {
            case "invalid_market_type" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Invalid market type",
                    null,
                    request);
            case "market_symbol_required" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Market symbol is required",
                    null,
                    request);
            case "invalid_market_symbol" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Invalid market symbol",
                    null,
                    request);
            case "invalid_market_range" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Invalid market range",
                    null,
                    request);
            case "invalid_market_interval" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Invalid market interval",
                    null,
                    request);
            case "invalid_market_before_open_time" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Invalid market beforeOpenTime",
                    null,
                    request);
            case "invalid_market_limit" -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Invalid market limit",
                    null,
                    request);
            default -> ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_request_failed",
                    "Market request failed",
                    null,
                    request);
        };
    }

    private String resolveMarketErrorCode(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "market_request_failed";
        }
        if (message.equals("invalid_market_type")
                || message.equals("market_symbol_required")
                || message.equals("invalid_market_symbol")
                || message.equals("invalid_market_range")
                || message.equals("invalid_market_interval")
                || message.equals("invalid_market_before_open_time")
                || message.equals("invalid_market_limit")) {
            return message;
        }
        return "market_request_failed";
    }
}
