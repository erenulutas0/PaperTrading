package com.finance.core.controller;

import com.finance.core.service.PerformanceAnalyticsService;
import com.finance.core.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
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
    public ResponseEntity<Map<String, Object>> getFullAnalytics(
            @PathVariable UUID portfolioId,
            @CurrentUserId(required = false) UUID userId) {
        UUID uid = userId != null ? userId : portfolioId; // fallback
        return ResponseEntity.ok(analyticsService.getFullAnalytics(portfolioId, uid));
    }

    @GetMapping("/{portfolioId}/export")
    public ResponseEntity<?> exportAnalytics(
            @PathVariable UUID portfolioId,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) String curveWindow,
            @RequestParam(required = false) String symbolFilter,
            @CurrentUserId(required = false) UUID userId) {
        UUID uid = userId != null ? userId : portfolioId;
        String normalizedFormat = format == null ? "json" : format.trim().toLowerCase();
        if ("csv".equals(normalizedFormat)) {
            byte[] content = analyticsService.buildAnalyticsExportCsv(portfolioId, uid, curveWindow, symbolFilter);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics-" + portfolioId + ".csv\"")
                    .contentType(new MediaType("text", "csv"))
                    .body(content);
        }

        String content = analyticsService.buildAnalyticsExportJson(portfolioId, uid, curveWindow, symbolFilter);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analytics-" + portfolioId + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
    }

    /** Risk metrics only */
    @GetMapping("/{portfolioId}/risk")
    public ResponseEntity<?> getRiskMetrics(@PathVariable UUID portfolioId) {
        Map<String, Object> risk = Map.of(
                "maxDrawdown", analyticsService.calculateMaxDrawdown(portfolioId),
                "sharpeRatio", analyticsService.calculateSharpeRatio(portfolioId),
                "sortinoRatio", analyticsService.calculateSortinoRatio(portfolioId),
                "volatility", analyticsService.calculateVolatility(portfolioId),
                "profitFactor", analyticsService.calculateProfitFactor(portfolioId));
        return ResponseEntity.ok(risk);
    }

    /** Trade statistics only */
    @GetMapping("/{portfolioId}/trades")
    public ResponseEntity<Map<String, Object>> getTradeStats(@PathVariable UUID portfolioId) {
        return ResponseEntity.ok(analyticsService.getTradeStats(portfolioId));
    }

    /** Equity curve data points */
    @GetMapping("/{portfolioId}/equity-curve")
    public ResponseEntity<?> getEquityCurve(@PathVariable UUID portfolioId) {
        return ResponseEntity.ok(analyticsService.getEquityCurve(portfolioId));
    }
}
