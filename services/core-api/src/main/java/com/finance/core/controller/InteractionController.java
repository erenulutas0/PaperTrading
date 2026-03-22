package com.finance.core.controller;

import com.finance.core.domain.Interaction;
import com.finance.core.dto.CommentResponse;
import com.finance.core.dto.InteractionRequest;
import com.finance.core.dto.InteractionSummaryResponse;
import com.finance.core.service.InteractionService;
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

import java.util.Locale;
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
        } catch (Exception e) {
            return toInteractionError(e, httpRequest);
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
        } catch (Exception e) {
            return toInteractionError(e, httpRequest);
        }
    }

    @GetMapping("/{targetId}/comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable UUID targetId,
            @RequestParam String type,
            @CurrentUserId(required = false) UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(interactionService.getComments(targetId, type, userId, pageable));
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

    private ResponseEntity<?> toInteractionError(Exception exception, HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : "Interaction request failed";
        String normalized = message.toLowerCase(Locale.ROOT);

        if (normalized.contains("target type is required")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "interaction_target_type_required",
                    "Target type is required", null, request);
        }
        if (normalized.contains("invalid target type")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "interaction_target_type_invalid",
                    "Invalid target type. Use PORTFOLIO, ANALYSIS_POST or COMMENT", null, request);
        }
        if (normalized.contains("comment content cannot be empty")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "interaction_comment_empty",
                    "Comment content cannot be empty", null, request);
        }
        if (normalized.contains("comment content cannot exceed 1000 characters")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "interaction_comment_too_long",
                    "Comment content cannot exceed 1000 characters", null, request);
        }
        if (normalized.contains("portfolio not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "portfolio_not_found", "Portfolio not found", null,
                    request);
        }
        if (normalized.contains("analysis post not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "analysis_post_not_found", "Analysis post not found",
                    null, request);
        }
        if (normalized.contains("comment not found") || normalized.contains("parent comment not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "comment_not_found", "Comment not found", null,
                    request);
        }

        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "interaction_request_failed", message, null, request);
    }
}
