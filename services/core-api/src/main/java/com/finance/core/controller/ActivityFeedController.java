package com.finance.core.controller;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.service.ActivityFeedService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.PageableRequestParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        Pageable effectivePageable = resolveFeedPageable(pageable, page, size);
        try {
            return ResponseEntity.ok(feedService.getPersonalizedFeed(userId, effectivePageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(org.springframework.http.HttpStatus.BAD_REQUEST, "feed_personalized_failed", "Failed to load personalized feed", null, request);
        }
    }

    /** Global feed: all activity */
    @GetMapping("/global")
    public ResponseEntity<Page<ActivityEvent>> getGlobalFeed(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = resolveFeedPageable(pageable, page, size);
        return ResponseEntity.ok(feedService.getGlobalFeed(effectivePageable));
    }

    /** A specific user's activity */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserActivity(
            @PathVariable UUID userId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        Pageable effectivePageable = resolveFeedPageable(pageable, page, size);
        try {
            return ResponseEntity.ok(feedService.getUserActivity(userId, effectivePageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(org.springframework.http.HttpStatus.BAD_REQUEST, "feed_user_activity_failed", "Failed to load user activity", null, request);
        }
    }

    private Pageable resolveFeedPageable(Pageable pageable, String rawPage, String rawSize) {
        return PageableRequestParser.resolvePageable(
                pageable,
                rawPage,
                rawSize,
                "invalid_feed_page",
                "Invalid feed page",
                "invalid_feed_size",
                "Invalid feed size");
    }
}
