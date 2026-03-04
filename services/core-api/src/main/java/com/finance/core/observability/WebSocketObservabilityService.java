package com.finance.core.observability;

import com.finance.core.config.WebSocketObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.websocket.observability.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketObservabilityService {

    private static final String EVENTS_METRIC = "app.websocket.events.total";
    private static final String ACTIVE_SESSIONS_METRIC = "websocket.sessions.active";
    private static final String RECONNECT_RATIO_METRIC = "app.websocket.reconnect.success.ratio";
    private static final String ANON_PREFIX = "anon-";

    private final MeterRegistry meterRegistry;
    private final WebSocketObservabilityProperties properties;

    private final AtomicInteger activeSessionsGauge = new AtomicInteger(0);
    private final AtomicReference<Double> reconnectSuccessRatioGauge = new AtomicReference<>(0.0);

    private final AtomicLong connectEvents = new AtomicLong(0);
    private final AtomicLong disconnectEvents = new AtomicLong(0);
    private final AtomicLong stompErrorEvents = new AtomicLong(0);
    private final AtomicLong reconnectCandidates = new AtomicLong(0);
    private final AtomicLong reconnectSuccesses = new AtomicLong(0);

    private final Set<String> activeSessionIds = ConcurrentHashMap.newKeySet();
    private final Map<String, String> sessionPrincipalBySessionId = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Instant>> pendingReconnectsByPrincipal = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> stompErrorsByCommand = new ConcurrentHashMap<>();

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge(ACTIVE_SESSIONS_METRIC, activeSessionsGauge);
        meterRegistry.gauge(RECONNECT_RATIO_METRIC, reconnectSuccessRatioGauge, AtomicReference::get);
    }

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String principal = accessor.getUser() != null ? accessor.getUser().getName() : null;
        recordConnectedSession(accessor.getSessionId(), principal);
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        String principal = event.getUser() != null ? event.getUser().getName() : null;
        recordDisconnectedSession(event.getSessionId(), principal);
    }

    public void recordConnectedSession(String sessionId, String principalName) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        String normalizedPrincipal = normalizePrincipal(principalName);
        if (activeSessionIds.add(sessionId)) {
            activeSessionsGauge.incrementAndGet();
            connectEvents.incrementAndGet();
            incrementEventCounter("connect", principalType(normalizedPrincipal));
        }

        if (isAuthenticatedPrincipal(normalizedPrincipal)) {
            sessionPrincipalBySessionId.put(sessionId, normalizedPrincipal);
            processReconnectSuccessIfAny(normalizedPrincipal, Instant.now());
        } else {
            sessionPrincipalBySessionId.remove(sessionId);
        }
    }

    public void recordDisconnectedSession(String sessionId, String principalName) {
        String normalizedPrincipal = normalizePrincipal(principalName);
        if ((normalizedPrincipal == null || normalizedPrincipal.isBlank()) && sessionId != null && !sessionId.isBlank()) {
            normalizedPrincipal = sessionPrincipalBySessionId.get(sessionId);
        }

        disconnectEvents.incrementAndGet();
        incrementEventCounter("disconnect", principalType(normalizedPrincipal));

        if (sessionId != null && !sessionId.isBlank()) {
            sessionPrincipalBySessionId.remove(sessionId);
            if (activeSessionIds.remove(sessionId)) {
                activeSessionsGauge.updateAndGet(current -> Math.max(0, current - 1));
            }
        }

        if (isAuthenticatedPrincipal(normalizedPrincipal)) {
            registerReconnectCandidate(normalizedPrincipal, Instant.now());
        }

        recalculateReconnectSuccessRatio();
    }

    public void recordStompError(String command, String reason) {
        String normalizedCommand = normalizeTagValue(command, "UNKNOWN");
        String normalizedReason = normalizeTagValue(reason, "unknown");

        stompErrorEvents.incrementAndGet();
        stompErrorsByCommand.computeIfAbsent(normalizedCommand, ignored -> new AtomicLong(0))
                .incrementAndGet();

        meterRegistry.counter(
                EVENTS_METRIC,
                "event", "stomp-error",
                "command", normalizedCommand,
                "reason", normalizedReason
        ).increment();
    }

    public WebSocketObservabilitySnapshot getSnapshot() {
        Map<String, Long> errorCounts = new TreeMap<>();
        for (Map.Entry<String, AtomicLong> entry : stompErrorsByCommand.entrySet()) {
            errorCounts.put(entry.getKey(), entry.getValue().get());
        }

        return new WebSocketObservabilitySnapshot(
                LocalDateTime.now(),
                activeSessionsGauge.get(),
                connectEvents.get(),
                disconnectEvents.get(),
                stompErrorEvents.get(),
                reconnectCandidates.get(),
                reconnectSuccesses.get(),
                reconnectSuccessRatioGauge.get(),
                errorCounts
        );
    }

    private void processReconnectSuccessIfAny(String principal, Instant now) {
        ConcurrentLinkedDeque<Instant> pending = pendingReconnectsByPrincipal.get(principal);
        if (pending == null) {
            recalculateReconnectSuccessRatio();
            return;
        }

        pruneExpired(pending, now);
        Instant candidate = pending.pollFirst();
        if (candidate != null) {
            reconnectSuccesses.incrementAndGet();
            incrementEventCounter("reconnect-success", "authenticated");
        }

        if (pending.isEmpty()) {
            pendingReconnectsByPrincipal.remove(principal, pending);
        }
        recalculateReconnectSuccessRatio();
    }

    private void registerReconnectCandidate(String principal, Instant now) {
        ConcurrentLinkedDeque<Instant> pending = pendingReconnectsByPrincipal
                .computeIfAbsent(principal, ignored -> new ConcurrentLinkedDeque<>());
        pruneExpired(pending, now);
        pending.addLast(now);
        reconnectCandidates.incrementAndGet();
    }

    private void pruneExpired(ConcurrentLinkedDeque<Instant> pending, Instant now) {
        Instant threshold = now.minus(properties.normalizedReconnectWindow());
        while (true) {
            Instant oldest = pending.peekFirst();
            if (oldest == null || !oldest.isBefore(threshold)) {
                break;
            }
            pending.pollFirst();
        }
    }

    private void incrementEventCounter(String event, String principalType) {
        meterRegistry.counter(
                EVENTS_METRIC,
                "event", event,
                "principal", principalType
        ).increment();
    }

    private void recalculateReconnectSuccessRatio() {
        long candidateCount = reconnectCandidates.get();
        if (candidateCount <= 0) {
            reconnectSuccessRatioGauge.set(0.0);
            return;
        }
        reconnectSuccessRatioGauge.set(((double) reconnectSuccesses.get()) / candidateCount);
    }

    private String normalizePrincipal(String principalName) {
        if (principalName == null) {
            return null;
        }
        String normalized = principalName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isAuthenticatedPrincipal(String principalName) {
        return principalName != null && !principalName.startsWith(ANON_PREFIX);
    }

    private String principalType(String principalName) {
        return isAuthenticatedPrincipal(principalName) ? "authenticated" : "anonymous";
    }

    private String normalizeTagValue(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().replace(' ', '-');
        if (value.length() > 64) {
            return value.substring(0, 64);
        }
        return value;
    }
}
