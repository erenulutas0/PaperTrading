package com.finance.core.controller;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.service.ActivityFeedService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
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
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(feedService.getPersonalizedFeed(userId, pageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(org.springframework.http.HttpStatus.BAD_REQUEST, "feed_personalized_failed", "Failed to load personalized feed", null, request);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(org.springframework.http.HttpStatus.BAD_REQUEST, "feed_user_activity_failed", "Failed to load user activity", null, request);
        }
    }
}
