package com.finance.core.controller;

import com.finance.core.dto.AnalysisPostRequest;
import com.finance.core.dto.AnalysisPostResponse;
import com.finance.core.service.AnalysisPostService;
import com.finance.core.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analysis-posts")
@RequiredArgsConstructor
public class AnalysisPostController {

    private final AnalysisPostService postService;

    /** Create a new analysis post. Server sets timestamp and snapshots price. */
    @PostMapping
    public ResponseEntity<AnalysisPostResponse> createPost(
            @CurrentUserId UUID userId,
            @RequestBody AnalysisPostRequest request) {
        return ResponseEntity.ok(postService.createPost(userId, request));
    }

    /** Soft-delete a post (tombstone). Only the author can do this. */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @CurrentUserId UUID userId) {
        postService.deletePost(postId, userId);
        return ResponseEntity.ok().build();
    }

    /** Get a single post */
    @GetMapping("/{postId}")
    public ResponseEntity<AnalysisPostResponse> getPost(@PathVariable UUID postId) {
        return ResponseEntity.ok(postService.getPost(postId));
    }

    /** Global feed: all analysis posts, newest first */
    @GetMapping("/feed")
    public ResponseEntity<Page<AnalysisPostResponse>> getFeed(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(postService.getFeed(pageable));
    }

    /** Get posts by a specific user */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<AnalysisPostResponse>> getPostsByUser(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(postService.getPostsByAuthor(userId, pageable));
    }

    /** Get author accuracy stats: total, hits, misses, pending */
    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<Map<String, Long>> getAuthorStats(@PathVariable UUID userId) {
        return ResponseEntity.ok(postService.getAuthorStats(userId));
    }
}
