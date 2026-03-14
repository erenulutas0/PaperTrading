package com.finance.core.controller;

import com.finance.core.dto.MarketTerminalLayoutRequest;
import com.finance.core.service.MarketTerminalLayoutService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<?> getLayouts(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(marketTerminalLayoutService.getLayouts(userId));
    }

    @PostMapping
    public ResponseEntity<?> createLayout(
            @CurrentUserId UUID userId,
            @RequestBody MarketTerminalLayoutRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(marketTerminalLayoutService.createLayout(userId, request));
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_terminal_layout_create_failed",
                    exception.getMessage(),
                    null,
                    httpRequest);
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
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_terminal_layout_update_failed",
                    exception.getMessage(),
                    null,
                    httpRequest);
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
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "market_terminal_layout_delete_failed",
                    exception.getMessage(),
                    null,
                    httpRequest);
        }
    }
}
