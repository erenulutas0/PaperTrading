package com.finance.core.controller;

import com.finance.core.domain.Notification;
import com.finance.core.security.JwtRuntimeProperties;
import com.finance.core.security.JwtTokenService;
import com.finance.core.service.NotificationService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
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
    private final JwtTokenService jwtTokenService;
    private final JwtRuntimeProperties jwtRuntimeProperties;

    /**
     * Set up a Server-Sent Events (SSE) connection.
     * The client will receive events starting with "data: " and event name
     * "notification".
     */
    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam("streamToken") String streamToken) {
        UUID userId = jwtTokenService.parseAndValidateNotificationStreamToken(streamToken);
        return notificationService.createEmitter(userId);
    }

    @GetMapping("/stream-token")
    public ResponseEntity<?> getStreamToken(
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            notificationService.ensureUserExists(userId);
            String streamToken = jwtTokenService.generateNotificationStreamToken(userId);
            long expiresInSeconds = jwtRuntimeProperties.normalizedNotificationStreamTokenTtl().getSeconds();
            return ResponseEntity.ok(Map.of(
                    "streamToken", streamToken,
                    "expiresInSeconds", expiresInSeconds));
        } catch (RuntimeException exception) {
            return buildNotificationError(exception, "notification_stream_token_failed", request);
        }
    }

    /**
     * Get paginated read/unread history.
     */
    @GetMapping
    public ResponseEntity<?> getNotifications(
            @CurrentUserId UUID userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
        } catch (RuntimeException exception) {
            return buildNotificationError(exception, "notifications_fetch_failed", request);
        }
    }

    /**
     * Fast lookup of unread notification count.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            long count = notificationService.getUnreadCount(userId);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (RuntimeException exception) {
            return buildNotificationError(exception, "notification_unread_count_failed", request);
        }
    }

    /**
     * Mark all as read.
     */
    @PostMapping("/mark-read")
    public ResponseEntity<?> markAllAsRead(
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            notificationService.markAllAsRead(userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException exception) {
            return buildNotificationError(exception, "notification_mark_all_read_failed", request);
        }
    }

    @PostMapping("/{notificationId}/mark-read")
    public ResponseEntity<?> markAsRead(
            @CurrentUserId UUID userId,
            @PathVariable("notificationId") String notificationId,
            HttpServletRequest httpRequest) {
        UUID parsedNotificationId;
        try {
            parsedNotificationId = UUID.fromString(notificationId);
        } catch (IllegalArgumentException exception) {
            return ApiErrorResponses.build(
                    HttpStatus.BAD_REQUEST,
                    "notification_id_invalid",
                    "Notification id must be a valid UUID",
                    null,
                    httpRequest);
        }
        boolean marked = notificationService.markAsRead(
                userId,
                parsedNotificationId);
        return marked
                ? ResponseEntity.ok().build()
                : ApiErrorResponses.build(
                        HttpStatus.NOT_FOUND,
                        "notification_not_found",
                        "Notification not found",
                        null,
                        httpRequest);
    }

    private ResponseEntity<?> buildNotificationError(
            RuntimeException exception,
            String fallbackCode,
            HttpServletRequest request) {
        if (exception instanceof ApiRequestException apiRequestException) {
            throw apiRequestException;
        }
        String message = exception.getMessage() != null ? exception.getMessage() : "";
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                fallbackCode,
                message,
                null,
                request);
    }
}
