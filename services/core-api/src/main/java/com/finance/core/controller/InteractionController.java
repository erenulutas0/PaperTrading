package com.finance.core.controller;

import com.finance.core.domain.Interaction;
import com.finance.core.dto.CommentResponse;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.dto.InteractionSummaryResponse;
import com.finance.core.service.InteractionService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.PageableRequestParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interactions")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    @PostMapping("/{targetId}/like")
    public ResponseEntity<?> toggleLike(
            @PathVariable UUID targetId,
            @CurrentUserId UUID userId,
            @RequestBody InteractionRequest request,
            HttpServletRequest httpRequest) {
        try {
            interactionService.toggleLike(userId, targetId, request);
            return ResponseEntity.ok().build();
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "interaction_request_failed",
                    "Interaction request failed",
                    null,
                    httpRequest);
        }
    }

    @PostMapping("/{targetId}/comments")
    public ResponseEntity<?> addComment(
            @PathVariable UUID targetId,
            @CurrentUserId UUID userId,
            @RequestBody InteractionRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(interactionService.addComment(userId, targetId, request));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "interaction_request_failed",
                    "Interaction request failed",
                    null,
                    httpRequest);
        }
    }

    @GetMapping("/{targetId}/comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable UUID targetId,
            @RequestParam String type,
            @CurrentUserId(required = false) UUID userId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = PageableRequestParser.resolvePageable(
                pageable,
                page,
                size,
                "invalid_interaction_comments_page",
                "Invalid interaction comments page",
                "invalid_interaction_comments_size",
                "Invalid interaction comments size");
        return ResponseEntity.ok(interactionService.getComments(targetId, type, userId, effectivePageable));
    }

    @GetMapping("/{targetId}/likes/count")
    public ResponseEntity<Map<String, Object>> getLikeInfo(
            @PathVariable UUID targetId,
            @RequestParam String type,
            @CurrentUserId(required = false) UUID userId) {

        long count = interactionService.getLikeCount(targetId, type);
        boolean hasLiked = false;

        if (userId != null) {
            hasLiked = interactionService.hasLiked(userId, targetId, type);
        }

        return ResponseEntity.ok(Map.of("count", count, "hasLiked", hasLiked));
    }

    @GetMapping("/{targetId}/summary")
    public ResponseEntity<InteractionSummaryResponse> getSummary(
            @PathVariable UUID targetId,
            @RequestParam String type,
            @CurrentUserId(required = false) UUID userId) {
        return ResponseEntity.ok(interactionService.getSummary(targetId, type, userId));
    }
}
