package com.finance.core.controller;

import com.finance.core.dto.StrategyBotRequest;
import com.finance.core.dto.PublicStrategyBotDetailResponse;
import com.finance.core.dto.PublicStrategyBotRunDetailResponse;
import com.finance.core.dto.StrategyBotAnalyticsResponse;
import com.finance.core.dto.StrategyBotBoardEntryResponse;
import com.finance.core.dto.StrategyBotRunReconciliationResponse;
import com.finance.core.dto.StrategyBotRunEquityPointResponse;
import com.finance.core.dto.StrategyBotRunEventResponse;
import com.finance.core.dto.StrategyBotRunFillResponse;
import com.finance.core.dto.StrategyBotResponse;
import com.finance.core.dto.StrategyBotRunRequest;
import com.finance.core.dto.StrategyBotRunResponse;
import com.finance.core.service.StrategyBotRunService;
import com.finance.core.service.StrategyBotService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/strategy-bots")
@RequiredArgsConstructor
public class StrategyBotController {

    private final StrategyBotService strategyBotService;
    private final StrategyBotRunService strategyBotRunService;

    @GetMapping
    public ResponseEntity<?> listBots(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotService.getUserBots(userId, pageable));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_list_failed", "Failed to load strategy bots", request);
        }
    }

    @GetMapping("/discover")
    public ResponseEntity<?> discoverBots(
            @RequestParam(defaultValue = "AVG_RETURN") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            @RequestParam(defaultValue = "") String q,
            @PageableDefault(size = 12) Pageable pageable,
            HttpServletRequest request) {
        try {
            Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.discoverPublicBotBoard(
                    pageable,
                    sortBy,
                    direction,
                    runMode,
                    lookbackDays,
                    q);
            return ResponseEntity.ok(board);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_discover_failed", "Failed to load public strategy bots", request);
        }
    }

    @GetMapping("/discover/{botId}")
    public ResponseEntity<?> getPublicBotDetail(
            @PathVariable UUID botId,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            HttpServletRequest request) {
        try {
            PublicStrategyBotDetailResponse detail = strategyBotRunService.getPublicBotDetail(botId, runMode, lookbackDays);
            return ResponseEntity.ok(detail);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_public_detail_failed", "Failed to load public strategy bot", request);
        }
    }

    @GetMapping("/discover/{botId}/runs/{runId}")
    public ResponseEntity<?> getPublicBotRunDetail(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            HttpServletRequest request) {
        try {
            PublicStrategyBotRunDetailResponse detail = strategyBotRunService.getPublicBotRunDetail(botId, runId);
            return ResponseEntity.ok(detail);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_public_run_detail_failed", "Failed to load public strategy bot run", request);
        }
    }

    @GetMapping("/discover/{botId}/runs/{runId}/export")
    public ResponseEntity<?> exportPublicBotRunDetail(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @RequestParam(defaultValue = "json") String format,
            HttpServletRequest request) {
        try {
            String normalizedFormat = normalizeExportFormat(format);
            if ("csv".equals(normalizedFormat)) {
                byte[] content = strategyBotRunService.buildPublicRunExportCsv(botId, runId);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-strategy-bot-run-" + runId + ".csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = strategyBotRunService.buildPublicRunExportJson(botId, runId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-strategy-bot-run-" + runId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_public_run_export_failed", "Failed to export public strategy bot run", request);
        }
    }

    @GetMapping("/discover/export")
    public ResponseEntity<?> exportPublicBotBoard(
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "AVG_RETURN") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            @RequestParam(defaultValue = "") String q,
            HttpServletRequest request) {
        try {
            String normalizedFormat = normalizeExportFormat(format);
            if ("csv".equals(normalizedFormat)) {
                byte[] content = strategyBotRunService.buildPublicBotBoardExportCsv(sortBy, direction, runMode, lookbackDays, q);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-strategy-bot-board.csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = strategyBotRunService.buildPublicBotBoardExportJson(sortBy, direction, runMode, lookbackDays, q);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-strategy-bot-board.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_public_board_export_failed", "Failed to export public strategy bots", request);
        }
    }

    @GetMapping("/discover/{botId}/export")
    public ResponseEntity<?> exportPublicBotDetail(
            @PathVariable UUID botId,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            HttpServletRequest request) {
        try {
            String normalizedFormat = normalizeExportFormat(format);
            if ("csv".equals(normalizedFormat)) {
                byte[] content = strategyBotRunService.buildPublicBotDetailExportCsv(botId, runMode, lookbackDays);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-strategy-bot-" + botId + ".csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = strategyBotRunService.buildPublicBotDetailExportJson(botId, runMode, lookbackDays);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"public-strategy-bot-" + botId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_public_detail_export_failed", "Failed to export public strategy bot", request);
        }
    }

    @GetMapping("/board")
    public ResponseEntity<?> getBotBoard(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "AVG_RETURN") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            @PageableDefault(size = 12) Pageable pageable,
            HttpServletRequest request) {
        try {
            Page<StrategyBotBoardEntryResponse> board = strategyBotRunService.getBotBoard(
                    userId,
                    pageable,
                    sortBy,
                    direction,
                    runMode,
                    lookbackDays);
            return ResponseEntity.ok(board);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_board_failed", "Failed to load strategy bot board", request);
        }
    }

    @GetMapping("/board/export")
    public ResponseEntity<?> exportBotBoard(
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "AVG_RETURN") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            HttpServletRequest request) {
        try {
            String normalizedFormat = normalizeExportFormat(format);
            if ("csv".equals(normalizedFormat)) {
                byte[] content = strategyBotRunService.buildBotBoardExportCsv(userId, sortBy, direction, runMode, lookbackDays);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strategy-bot-board.csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = strategyBotRunService.buildBotBoardExportJson(userId, sortBy, direction, runMode, lookbackDays);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strategy-bot-board.json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_board_export_failed", "Failed to export strategy bot board", request);
        }
    }

    @GetMapping("/{botId}")
    public ResponseEntity<?> getBot(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotService.getBot(botId, userId));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_read_failed", "Failed to load strategy bot", request);
        }
    }

    @GetMapping("/{botId}/analytics")
    public ResponseEntity<?> getBotAnalytics(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            HttpServletRequest request) {
        try {
            StrategyBotAnalyticsResponse analytics = strategyBotRunService.getBotAnalytics(botId, userId, runMode, lookbackDays);
            return ResponseEntity.ok(analytics);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_analytics_failed", "Failed to load strategy bot analytics", request);
        }
    }

    @GetMapping("/{botId}/analytics/export")
    public ResponseEntity<?> exportBotAnalytics(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(defaultValue = "ALL") String runMode,
            @RequestParam(required = false) Integer lookbackDays,
            HttpServletRequest request) {
        try {
            String normalizedFormat = normalizeExportFormat(format);
            if ("csv".equals(normalizedFormat)) {
                byte[] content = strategyBotRunService.buildBotAnalyticsExportCsv(botId, userId, runMode, lookbackDays);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strategy-bot-analytics-" + botId + ".csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = strategyBotRunService.buildBotAnalyticsExportJson(botId, userId, runMode, lookbackDays);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strategy-bot-analytics-" + botId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_analytics_export_failed", "Failed to export strategy bot analytics", request);
        }
    }

    @PostMapping
    public ResponseEntity<?> createBot(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) StrategyBotRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(strategyBotService.createBot(userId, request));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_create_failed", "Failed to create strategy bot", httpRequest);
        }
    }

    @PutMapping("/{botId}")
    public ResponseEntity<?> updateBot(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            @RequestBody(required = false) StrategyBotRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(strategyBotService.updateBot(botId, userId, request));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_update_failed", "Failed to update strategy bot", httpRequest);
        }
    }

    @DeleteMapping("/{botId}")
    public ResponseEntity<?> deleteBot(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            strategyBotService.deleteBot(botId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_delete_failed", "Failed to delete strategy bot", request);
        }
    }

    @GetMapping("/{botId}/runs")
    public ResponseEntity<?> listRuns(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        try {
            Page<StrategyBotRunResponse> runs = strategyBotRunService.getRuns(botId, userId, pageable);
            return ResponseEntity.ok(runs);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_runs_failed", "Failed to load strategy bot runs", request);
        }
    }

    @GetMapping("/{botId}/runs/{runId}")
    public ResponseEntity<?> getRun(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotRunService.getRun(botId, runId, userId));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_read_failed", "Failed to load strategy bot run", request);
        }
    }

    @GetMapping("/{botId}/runs/{runId}/export")
    public ResponseEntity<?> exportRun(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            @RequestParam(defaultValue = "json") String format,
            HttpServletRequest request) {
        try {
            String normalizedFormat = normalizeExportFormat(format);
            if ("csv".equals(normalizedFormat)) {
                byte[] content = strategyBotRunService.buildRunExportCsv(botId, runId, userId);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strategy-bot-run-" + runId + ".csv\"")
                        .contentType(new MediaType("text", "csv"))
                        .body(content);
            }

            String content = strategyBotRunService.buildRunExportJson(botId, runId, userId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"strategy-bot-run-" + runId + ".json\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(content);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_export_failed", "Failed to export strategy bot run", request);
        }
    }

    @GetMapping("/{botId}/runs/{runId}/fills")
    public ResponseEntity<?> listRunFills(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 50) Pageable pageable,
            HttpServletRequest request) {
        try {
            Page<StrategyBotRunFillResponse> fills = strategyBotRunService.getRunFills(botId, runId, userId, pageable);
            return ResponseEntity.ok(fills);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_fills_failed", "Failed to load strategy bot run fills", request);
        }
    }

    @GetMapping("/{botId}/runs/{runId}/events")
    public ResponseEntity<?> listRunEvents(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 100) Pageable pageable,
            HttpServletRequest request) {
        try {
            Page<StrategyBotRunEventResponse> events = strategyBotRunService.getRunEvents(botId, runId, userId, pageable);
            return ResponseEntity.ok(events);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_events_failed", "Failed to load strategy bot run events", request);
        }
    }

    @GetMapping("/{botId}/runs/{runId}/equity-curve")
    public ResponseEntity<?> listRunEquityCurve(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            @PageableDefault(size = 100) Pageable pageable,
            HttpServletRequest request) {
        try {
            Page<StrategyBotRunEquityPointResponse> points = strategyBotRunService.getRunEquityCurve(botId, runId, userId, pageable);
            return ResponseEntity.ok(points);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_equity_curve_failed", "Failed to load strategy bot run equity curve", request);
        }
    }

    @GetMapping("/{botId}/runs/{runId}/reconciliation-plan")
    public ResponseEntity<?> getRunReconciliation(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            StrategyBotRunReconciliationResponse plan = strategyBotRunService.getRunReconciliation(botId, runId, userId);
            return ResponseEntity.ok(plan);
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_reconciliation_failed", "Failed to load strategy bot reconciliation plan", request);
        }
    }

    @PostMapping("/{botId}/runs/{runId}/apply-reconciliation")
    public ResponseEntity<?> applyRunReconciliation(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotRunService.applyRunReconciliation(botId, runId, userId));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_reconciliation_apply_failed", "Failed to apply strategy bot reconciliation", request);
        }
    }

    @PostMapping("/{botId}/runs")
    public ResponseEntity<?> requestRun(
            @PathVariable UUID botId,
            @CurrentUserId UUID userId,
            @RequestBody(required = false) StrategyBotRunRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(strategyBotRunService.requestRun(botId, userId, request));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_request_failed", "Failed to request strategy bot run", httpRequest);
        }
    }

    @PostMapping("/{botId}/runs/{runId}/execute")
    public ResponseEntity<?> executeRun(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotRunService.executeRun(botId, runId, userId));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_execute_failed", "Failed to execute strategy bot run", request);
        }
    }

    @PostMapping("/{botId}/runs/{runId}/refresh")
    public ResponseEntity<?> refreshForwardTestRun(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotRunService.refreshForwardTestRun(botId, runId, userId));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_refresh_failed", "Failed to refresh strategy bot run", request);
        }
    }

    @PostMapping("/{botId}/runs/{runId}/cancel")
    public ResponseEntity<?> cancelRun(
            @PathVariable UUID botId,
            @PathVariable UUID runId,
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(strategyBotRunService.cancelRun(botId, runId, userId));
        } catch (Exception ex) {
            return buildBotError(ex, "strategy_bot_run_cancel_failed", "Failed to cancel strategy bot run", request);
        }
    }

    private ResponseEntity<?> buildBotError(Exception exception, String fallbackCode, String fallbackMessage, HttpServletRequest request) {
        if (exception instanceof ApiRequestException apiRequestException) {
            throw apiRequestException;
        }
        String message = exception.getMessage();
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                fallbackCode,
                message == null || message.isBlank() ? fallbackMessage : message,
                null,
                request);
    }

    private String normalizeExportFormat(String format) {
        String normalizedFormat = format == null ? "json" : format.trim().toLowerCase(Locale.ROOT);
        if ("json".equals(normalizedFormat) || "csv".equals(normalizedFormat)) {
            return normalizedFormat;
        }
        throw ApiRequestException.badRequest("invalid_strategy_bot_export_format", "Invalid strategy bot export format");
    }
}
