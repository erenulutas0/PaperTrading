package com.finance.core.controller;

import com.finance.core.service.PerformanceAnalyticsService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final PerformanceAnalyticsService analyticsService;

    /**
     * Full analytics dashboard for a portfolio.
     * Includes risk metrics, trade stats, and equity curve.
     */
    @GetMapping("/{portfolioId}")
    public ResponseEntity<?> getFullAnalytics(
            @PathVariable UUID portfolioId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(analyticsService.getFullAnalytics(portfolioId));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.INTERNAL_SERVER_ERROR, "analytics_read_failed", "Failed to load analytics", null, request);
        }
    }

    @GetMapping("/{portfolioId}/export")
    public ResponseEntity<?> exportAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) String curveWindow,
            @RequestParam(required = false) String symbolFilter,
            HttpServletRequest request) {
        String normalizedFormat = normalizeFormat(format);
        try {
            if ("csv".equals(normalizedFormat)) {
                byte[] content = analyticsService.buildAnalyticsExportCsv(portfolioId, curveWindow, symbolFilter);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics-" + portfolioId + ".csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = analyticsService.buildAnalyticsExportJson(portfolioId, curveWindow, symbolFilter);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics-" + portfolioId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.INTERNAL_SERVER_ERROR, "analytics_export_failed", "Failed to export analytics", null, request);
        }
    }

    /** Risk metrics only */
    @GetMapping("/{portfolioId}/risk")
    public ResponseEntity<?> getRiskMetrics(@PathVariable UUID portfolioId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(analyticsService.getRiskMetrics(portfolioId));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.INTERNAL_SERVER_ERROR, "analytics_risk_failed", "Failed to load analytics risk metrics", null, request);
        }
    }

    /** Trade statistics only */
    @GetMapping("/{portfolioId}/trades")
    public ResponseEntity<?> getTradeStats(@PathVariable UUID portfolioId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(analyticsService.getTradeStats(portfolioId));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.INTERNAL_SERVER_ERROR, "analytics_trades_failed", "Failed to load analytics trade stats", null, request);
        }
    }

    /** Equity curve data points */
    @GetMapping("/{portfolioId}/equity-curve")
    public ResponseEntity<?> getEquityCurve(@PathVariable UUID portfolioId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(analyticsService.getEquityCurve(portfolioId));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.INTERNAL_SERVER_ERROR, "analytics_equity_curve_failed", "Failed to load analytics equity curve", null, request);
        }
    }

    private String normalizeFormat(String format) {
        String normalized = format == null ? "json" : format.trim().toLowerCase(Locale.ROOT);
        if (!"csv".equals(normalized) && !"json".equals(normalized)) {
            throw ApiRequestException.badRequest("invalid_analytics_export_format", "Invalid analytics export format");
        }
        return normalized;
    }
}
