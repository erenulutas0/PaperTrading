package com.finance.core.controller;

import com.finance.core.dto.StrategyBotRequest;
import com.finance.core.dto.StrategyBotResponse;
import com.finance.core.service.StrategyBotService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/strategy-bots")
@RequiredArgsConstructor
public class StrategyBotController {

    private final StrategyBotService strategyBotService;

    @GetMapping
    public ResponseEntity<Page<StrategyBotResponse>> listBots(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(strategyBotService.getUserBots(userId, pageable));
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

    private ResponseEntity<?> buildBotError(Exception exception, String fallbackCode, String fallbackMessage, HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : fallbackMessage;
        String normalized = message.toLowerCase();

        if (normalized.contains("strategy bot not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "strategy_bot_not_found", "Strategy bot not found", null, request);
        }
        if (normalized.contains("linked portfolio not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "linked_portfolio_not_found", "Linked portfolio not found", null, request);
        }
        if (normalized.contains("invalid strategy bot status")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "invalid_strategy_bot_status", "Invalid strategy bot status", null, request);
        }

        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, fallbackCode, message, null, request);
    }
}
