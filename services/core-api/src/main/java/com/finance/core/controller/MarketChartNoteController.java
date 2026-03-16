package com.finance.core.controller;

import com.finance.core.dto.MarketChartNoteRequest;
import com.finance.core.dto.MarketChartNoteResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.service.MarketChartNoteService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/market/chart-notes")
@RequiredArgsConstructor
public class MarketChartNoteController {

    private final MarketChartNoteService marketChartNoteService;

    @GetMapping
    public ResponseEntity<Page<MarketChartNoteResponse>> getNotes(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) MarketType market,
            @RequestParam String symbol,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(marketChartNoteService.getNotes(userId, market, symbol, pageable));
    }

    @PostMapping
    public ResponseEntity<?> createNote(
            @CurrentUserId UUID userId,
            @RequestBody MarketChartNoteRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(marketChartNoteService.createNote(userId, request));
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_chart_note_create_failed",
                    exception.getMessage(),
                    null,
                    httpRequest);
        }
    }

    @PutMapping("/{noteId}")
    public ResponseEntity<?> updateNote(
            @PathVariable UUID noteId,
            @CurrentUserId UUID userId,
            @RequestBody MarketChartNoteRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(marketChartNoteService.updateNote(userId, noteId, request));
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "market_chart_note_update_failed",
                    exception.getMessage(),
                    null,
                    httpRequest);
        }
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<?> deleteNote(
            @PathVariable UUID noteId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            marketChartNoteService.deleteNote(userId, noteId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "market_chart_note_delete_failed",
                    exception.getMessage(),
                    null,
                    httpRequest);
        }
    }
}
