package com.finance.core.controller;

import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.dto.AnalysisPostResponse;
import com.finance.core.service.AnalysisPostService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analysis-posts")
@RequiredArgsConstructor
public class AnalysisPostController {

    private final AnalysisPostService postService;

    /** Create a new analysis post. Server sets timestamp and snapshots price. */
    @PostMapping
    public ResponseEntity<?> createPost(
            @CurrentUserId UUID userId,
            @RequestBody AnalysisPostRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.createPost(userId, request));
        } catch (RuntimeException exception) {
            return buildAnalysisError(exception, "analysis_post_create_failed", "Failed to create analysis post", httpRequest);
        }
    }

    /** Soft-delete a post (tombstone). Only the author can do this. */
    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(
            @PathVariable UUID postId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            postService.deletePost(postId, userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException exception) {
            return buildAnalysisError(exception, "analysis_post_delete_failed", "Failed to delete analysis post", httpRequest);
        }
    }

    /** Get a single post */
    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@PathVariable UUID postId, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.getPost(postId));
        } catch (RuntimeException exception) {
            return buildAnalysisError(exception, "analysis_post_read_failed", "Failed to read analysis post", httpRequest);
        }
    }

    /** Global feed: all analysis posts, newest first */
    @GetMapping("/feed")
    public ResponseEntity<?> getFeed(
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.getFeed(pageable));
        } catch (RuntimeException exception) {
            return buildAnalysisError(exception, "analysis_post_feed_failed", "Failed to load analysis feed", httpRequest);
        }
    }

    /** Get posts by a specific user */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.getPostsByAuthor(userId, pageable));
        } catch (RuntimeException exception) {
            return buildAnalysisError(exception, "analysis_post_author_feed_failed", "Failed to load author analysis posts", httpRequest);
        }
    }

    /** Get author accuracy stats: total, hits, misses, pending */
    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<?> getAuthorStats(@PathVariable UUID userId, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.getAuthorStats(userId));
        } catch (RuntimeException exception) {
            return buildAnalysisError(exception, "analysis_post_stats_failed", "Failed to load analysis author stats", httpRequest);
        }
    }

    private ResponseEntity<?> buildAnalysisError(
            RuntimeException exception,
            String fallbackCode,
            String fallbackMessage,
            HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : fallbackMessage;
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("user not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "user_not_found", "User not found", null, request);
        }
        if (normalized.contains("post not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "analysis_post_not_found", "Analysis post not found", null, request);
        }
        if (normalized.contains("author not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "analysis_post_author_not_found", "Analysis post author not found", null, request);
        }
        if (normalized.contains("only the author can delete")) {
            return ApiErrorResponses.build(HttpStatus.FORBIDDEN, "analysis_post_delete_forbidden", "Only the author can delete their post", null, request);
        }
        if (normalized.contains("already deleted")) {
            return ApiErrorResponses.build(HttpStatus.CONFLICT, "analysis_post_already_deleted", "Analysis post already deleted", null, request);
        }
        if (normalized.contains("invalid direction")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "invalid_analysis_direction", "Invalid analysis direction", null, request);
        }
        if (normalized.contains("no market data")) {
            return ApiErrorResponses.build(HttpStatus.CONFLICT, "analysis_market_data_unavailable", message, null, request);
        }
        if (normalized.contains("target price must be above") || normalized.contains("target price must be below")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "invalid_analysis_target_price", message, null, request);
        }
        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, fallbackCode, message, null, request);
    }
}
