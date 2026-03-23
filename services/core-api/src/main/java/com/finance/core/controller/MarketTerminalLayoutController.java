package com.finance.core.controller;

import com.finance.core.dto.MarketTerminalLayoutRequest;
import com.finance.core.service.MarketTerminalLayoutService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.PageableRequestParser;
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
            @org.springframework.web.bind.annotation.RequestParam(required = false) String page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = PageableRequestParser.resolvePageable(
                pageable,
                page,
                size,
                "invalid_market_terminal_layout_page",
                "Invalid market terminal layout page",
                "invalid_market_terminal_layout_size",
                "Invalid market terminal layout size");
        return ResponseEntity.ok(marketTerminalLayoutService.getLayouts(userId, effectivePageable));
    }

    @PostMapping
    public ResponseEntity<?> createLayout(
            @CurrentUserId UUID userId,
            @RequestBody MarketTerminalLayoutRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(marketTerminalLayoutService.createLayout(userId, request));
        } catch (RuntimeException exception) {
            return toLayoutError(
                    exception,
                    httpRequest,
                    "market_terminal_layout_create_failed",
                    "Failed to create market terminal layout");
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
            return toLayoutError(
                    exception,
                    httpRequest,
                    "market_terminal_layout_update_failed",
                    "Failed to update market terminal layout");
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
            return toLayoutError(
                    exception,
                    httpRequest,
                    "market_terminal_layout_delete_failed",
                    "Failed to delete market terminal layout");
        }
    }

    private ResponseEntity<?> toLayoutError(
            RuntimeException exception,
            HttpServletRequest httpRequest,
            String fallbackCode,
            String fallbackMessage) {
        if (exception instanceof ApiRequestException apiRequestException) {
            throw apiRequestException;
        }
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                fallbackCode,
                fallbackMessage,
                null,
                httpRequest);
    }
}
