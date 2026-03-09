package com.finance.core.service;

import com.finance.core.config.WebSocketRuntimeProperties;
import com.finance.core.domain.Notification;
import com.finance.core.domain.event.NotificationEvent;
import com.finance.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketRuntimeProperties webSocketRuntimeProperties;

    // Concurrent map to hold open SSE connections per user (legacy fallback)
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Subscribe a user to their real-time notification stream (SSE fallback).
     */
    public SseEmitter createEmitter(UUID userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        SseEmitter previous = emitters.put(userId, emitter);
        if (previous != null) {
            previous.complete();
        }

        emitter.onCompletion(() -> {
            log.debug("SSE completion for user: {}", userId);
            emitters.remove(userId);
        });
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for user: {}", userId);
            emitters.remove(userId);
        });
        emitter.onError((e) -> {
            log.debug("SSE error for user: {}", userId, e);
            emitters.remove(userId);
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("status", "connected")));
        } catch (IOException e) {
            emitters.remove(userId);
            throw new IllegalStateException("Failed to initialize notification stream", e);
        }

        return emitter;
    }

    /**
     * Listen for asynchronously dispatched events, save to DB, and push to client
     * via both WebSocket/STOMP and SSE.
     */
    @Async
    @EventListener
    @Transactional
    public void handleNotificationEvent(NotificationEvent event) {
        // Prevent users from notifying themselves (e.g., liking own post)
        if (event.getActorId() != null && event.getReceiverId().equals(event.getActorId())) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(event.getReceiverId())
                .actorId(event.getActorId())
                .actorUsername(event.getActorUsername())
                .type(event.getType())
                .referenceId(event.getReferenceId())
                .referenceLabel(event.getReferenceLabel())
                .build();

        notification = notificationRepository.save(notification);
        log.info("Saved notification {} for user {}", notification.getType(), notification.getUserId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", notification.getId() != null ? notification.getId() : "");
        payload.put("type", notification.getType() != null ? notification.getType().name() : "");
        payload.put("actorUsername", notification.getActorUsername() != null ? notification.getActorUsername() : "System");
        payload.put("referenceId", notification.getReferenceId() != null ? notification.getReferenceId() : "");
        payload.put("referenceLabel", notification.getReferenceLabel() != null ? notification.getReferenceLabel() : "");
        payload.put("read", false);
        payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "");

        // ===== WebSocket push (primary) =====
        try {
            String destination = "/queue/notifications";
            messagingTemplate.convertAndSendToUser(
                    event.getReceiverId().toString(),
                    destination,
                    payload);

            if (webSocketRuntimeProperties.isLegacyUserTopicBroadcastEnabled()) {
                // Secondary channel for legacy clients subscribing by explicit user topic.
                messagingTemplate.convertAndSend(
                        "/topic/notifications/" + event.getReceiverId(),
                        payload);
            }
            log.debug("WebSocket notification pushed to user {}", event.getReceiverId());
        } catch (Exception e) {
            log.debug("WebSocket push failed for user {}: {}", event.getReceiverId(), e.getMessage());
        }

        // ===== SSE push (fallback) =====
        SseEmitter emitter = emitters.get(event.getReceiverId());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.debug("Failed to push notification to SSE client {}, removing emitter.", event.getReceiverId());
                emitters.remove(event.getReceiverId());
            }
        }
    }

    public Page<Notification> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsReadForUser(userId);
    }

    @Transactional
    public boolean markAsRead(UUID userId, UUID notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .map(notification -> {
                    if (!notification.isRead()) {
                        notification.setRead(true);
                    }
                    return true;
                })
                .orElse(false);
    }
}
