package com.finance.core.controller;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.service.ActivityFeedService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
public class ActivityFeedController {

    private final ActivityFeedService feedService;

    /** Personalized feed: events from followed users */
    @GetMapping
    public ResponseEntity<?> getPersonalizedFeed(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(feedService.getPersonalizedFeed(userId, pageable));
        } catch (RuntimeException exception) {
            return buildFeedError(exception, "feed_personalized_failed", "Failed to load personalized feed", request);
        }
    }

    /** Global feed: all activity */
    @GetMapping("/global")
    public ResponseEntity<Page<ActivityEvent>> getGlobalFeed(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(feedService.getGlobalFeed(pageable));
    }

    /** A specific user's activity */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserActivity(
            @PathVariable UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(feedService.getUserActivity(userId, pageable));
        } catch (RuntimeException exception) {
            return buildFeedError(exception, "feed_user_activity_failed", "Failed to load user activity", request);
        }
    }

    private ResponseEntity<?> buildFeedError(
            RuntimeException exception,
            String fallbackCode,
            String fallbackMessage,
            HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : fallbackMessage;
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("user not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "user_not_found", "User not found", null, request);
        }
        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, fallbackCode, message, null, request);
    }
}
