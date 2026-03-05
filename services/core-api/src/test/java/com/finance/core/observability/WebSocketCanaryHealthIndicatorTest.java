package com.finance.core.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketCanaryHealthIndicatorTest {

    private WebSocketCanaryService canaryService;
    private WebSocketCanaryHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        canaryService = mock(WebSocketCanaryService.class);
        indicator = new WebSocketCanaryHealthIndicator(canaryService);
    }

    @Test
    void health_shouldStayUp_whenWarningStateAndSingleFailure() {
        WebSocketCanarySnapshot snapshot = new WebSocketCanarySnapshot(
                LocalDateTime.now(),
                false,
                1,
                0,
                2,
                3,
                1,
                1,
                1.0,
                "WARNING",
                1000L,
                "ws://localhost:8080/ws",
                "/topic/ops/canary",
                "/queue/ops-canary",
                false,
                false,
                "message-delivery-timeout"
        );
        when(canaryService.getLatestSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("WARNING", health.getDetails().get("alertState"));
    }

    @Test
    void health_shouldBeDown_whenCriticalStateReached() {
        WebSocketCanarySnapshot snapshot = new WebSocketCanarySnapshot(
                LocalDateTime.now(),
                false,
                3,
                0,
                2,
                3,
                3,
                3,
                1.0,
                "CRITICAL",
                5000L,
                "ws://localhost:8080/ws",
                "/topic/ops/canary",
                "/queue/ops-canary",
                false,
                false,
                "timeout"
        );
        when(canaryService.getLatestSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("CRITICAL", health.getDetails().get("alertState"));
    }
}
