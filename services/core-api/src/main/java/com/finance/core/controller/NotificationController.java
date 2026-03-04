package com.finance.core.controller;

import com.finance.core.domain.Notification;
import com.finance.core.service.NotificationService;
import com.finance.core.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Set up a Server-Sent Events (SSE) connection.
     * The client will receive events starting with "data: " and event name
     * "notification".
     */
    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam("userId") String userId) {
        return notificationService.createEmitter(UUID.fromString(userId));
    }

    /**
     * Get paginated read/unread history.
     */
    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
    }

    /**
     * Fast lookup of unread notification count.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @CurrentUserId UUID userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * Mark all as read.
     */
    @PostMapping("/mark-read")
    public ResponseEntity<Void> markAllAsRead(@CurrentUserId UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{notificationId}/mark-read")
    public ResponseEntity<Void> markAsRead(
            @CurrentUserId UUID userId,
            @PathVariable("notificationId") String notificationId) {
        boolean marked = notificationService.markAsRead(
                userId,
                UUID.fromString(notificationId));
        return marked ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
