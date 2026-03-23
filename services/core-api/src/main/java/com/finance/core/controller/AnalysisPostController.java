package com.finance.core.controller;

import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.dto.AnalysisPostResponse;
import com.finance.core.service.AnalysisPostService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.PageableRequestParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "analysis_post_create_failed", "Failed to create analysis post", null, httpRequest);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "analysis_post_delete_failed", "Failed to delete analysis post", null, httpRequest);
        }
    }

    /** Get a single post */
    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@PathVariable UUID postId, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.getPost(postId));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "analysis_post_read_failed", "Failed to read analysis post", null, httpRequest);
        }
    }

    /** Global feed: all analysis posts, newest first */
    @GetMapping("/feed")
    public ResponseEntity<?> getFeed(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = resolveAnalysisPostPageable(pageable, page, size);
        try {
            return ResponseEntity.ok(postService.getFeed(effectivePageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "analysis_post_feed_failed", "Failed to load analysis feed", null, httpRequest);
        }
    }

    /** Get posts by a specific user */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = resolveAnalysisPostPageable(pageable, page, size);
        try {
            return ResponseEntity.ok(postService.getPostsByAuthor(userId, effectivePageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "analysis_post_author_feed_failed", "Failed to load author analysis posts", null, httpRequest);
        }
    }

    /** Get author accuracy stats: total, hits, misses, pending */
    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<?> getAuthorStats(@PathVariable UUID userId, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(postService.getAuthorStats(userId));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "analysis_post_stats_failed", "Failed to load analysis author stats", null, httpRequest);
        }
    }

    private Pageable resolveAnalysisPostPageable(Pageable pageable, String rawPage, String rawSize) {
        return PageableRequestParser.resolvePageable(
                pageable,
                rawPage,
                rawSize,
                "invalid_analysis_post_page",
                "Invalid analysis post page",
                "invalid_analysis_post_size",
                "Invalid analysis post size");
    }
}
