package com.finance.core.controller;

import com.finance.core.dto.MarketTerminalLayoutRequest;
import com.finance.core.service.MarketTerminalLayoutService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/preferences/terminal-layouts")
@RequiredArgsConstructor
public class MarketTerminalLayoutController {

    private final MarketTerminalLayoutService marketTerminalLayoutService;

    @GetMapping
    public ResponseEntity<?> getLayouts(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(marketTerminalLayoutService.getLayouts(userId, pageable));
    }

    @PostMapping
    public ResponseEntity<?> createLayout(
            @CurrentUserId UUID userId,
            @RequestBody MarketTerminalLayoutRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(marketTerminalLayoutService.createLayout(userId, request));
        } catch (RuntimeException exception) {
            return toLayoutError(exception, httpRequest, true);
        }
    }

    @PutMapping("/{layoutId}")
    public ResponseEntity<?> updateLayout(
            @CurrentUserId UUID userId,
            @PathVariable UUID layoutId,
            @RequestBody MarketTerminalLayoutRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(marketTerminalLayoutService.updateLayout(userId, layoutId, request));
        } catch (RuntimeException exception) {
            return toLayoutError(exception, httpRequest, false);
        }
    }

    @DeleteMapping("/{layoutId}")
    public ResponseEntity<?> deleteLayout(
            @CurrentUserId UUID userId,
            @PathVariable UUID layoutId,
            HttpServletRequest httpRequest) {
        try {
            marketTerminalLayoutService.deleteLayout(userId, layoutId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException exception) {
            return toLayoutError(exception, httpRequest, false);
        }
    }

    private ResponseEntity<?> toLayoutError(
            RuntimeException exception,
            HttpServletRequest httpRequest,
            boolean createPath) {
        String message = exception.getMessage() != null ? exception.getMessage() : "";
        if ("Layout limit reached".equals(message)) {
            return ApiErrorResponses.build(
                    HttpStatus.CONFLICT,
                    "market_terminal_layout_limit_reached",
                    message,
                    null,
                    httpRequest);
        }
        if ("Layout name is required".equals(message)) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_terminal_layout_name_required",
                    message,
                    null,
                    httpRequest);
        }
        if ("Layout name exceeds 80 characters".equals(message)) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_terminal_layout_name_too_long",
                    message,
                    null,
                    httpRequest);
        }
        if ("Terminal layout not found".equals(message)) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "market_terminal_layout_not_found",
                    message,
                    null,
                    httpRequest);
        }
        if ("User not found".equals(message)) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "user_not_found",
                    message,
                    null,
                    httpRequest);
        }
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                createPath ? "market_terminal_layout_create_failed" : "market_terminal_layout_update_failed",
                message,
                null,
                httpRequest);
    }
}
