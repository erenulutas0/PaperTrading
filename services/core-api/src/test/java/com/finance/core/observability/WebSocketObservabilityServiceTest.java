package com.finance.core.observability;

import com.finance.core.config.WebSocketObservabilityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebSocketObservabilityServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private WebSocketObservabilityService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        WebSocketObservabilityProperties properties = new WebSocketObservabilityProperties();
        properties.setReconnectWindow(Duration.ofMinutes(2));
        service = new WebSocketObservabilityService(meterRegistry, properties);
        service.registerMeters();
    }

    @Test
    void reconnectFlow_shouldTrackCandidatesSuccessAndRatio() {
        service.recordConnectedSession("session-1", "user-1");
        service.recordDisconnectedSession("session-1", "user-1");

        WebSocketObservabilitySnapshot afterDisconnect = service.getSnapshot();
        assertEquals(0, afterDisconnect.activeSessions());
        assertEquals(1, afterDisconnect.connectEvents());
        assertEquals(1, afterDisconnect.disconnectEvents());
        assertEquals(1, afterDisconnect.reconnectCandidates());
        assertEquals(0, afterDisconnect.reconnectSuccesses());
        assertEquals(0.0, afterDisconnect.reconnectSuccessRatio());

        service.recordConnectedSession("session-2", "user-1");

        WebSocketObservabilitySnapshot afterReconnect = service.getSnapshot();
        assertEquals(1, afterReconnect.activeSessions());
        assertEquals(2, afterReconnect.connectEvents());
        assertEquals(1, afterReconnect.disconnectEvents());
        assertEquals(1, afterReconnect.reconnectCandidates());
        assertEquals(1, afterReconnect.reconnectSuccesses());
        assertEquals(1.0, afterReconnect.reconnectSuccessRatio());
    }

    @Test
    void recordStompError_shouldIncrementCountersAndSnapshotMap() {
        service.recordStompError("SUBSCRIBE", "legacy-topic-disabled");
        service.recordStompError("SUBSCRIBE", "legacy-topic-disabled");

        WebSocketObservabilitySnapshot snapshot = service.getSnapshot();
        assertEquals(2, snapshot.stompErrorEvents());
        assertEquals(2L, snapshot.stompErrorsByCommand().get("SUBSCRIBE"));

        Counter counter = meterRegistry.find("app.websocket.events.total")
                .tags("event", "stomp-error", "command", "SUBSCRIBE", "reason", "legacy-topic-disabled")
                .counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }
}
