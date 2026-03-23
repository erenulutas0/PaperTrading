package com.finance.core.observability;

import com.finance.core.config.StrategyBotForwardTestObservabilityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StrategyBotForwardTestHealthIndicatorTest {

    private StrategyBotForwardTestObservabilityService service;
    private StrategyBotForwardTestObservabilityProperties properties;
    private StrategyBotForwardTestHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        service = mock(StrategyBotForwardTestObservabilityService.class);
        properties = new StrategyBotForwardTestObservabilityProperties();
        indicator = new StrategyBotForwardTestHealthIndicator(service, properties);
    }

    @Test
    void health_shouldStayUnknownWhileAwaitingFirstTickInsideStartupGrace() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot snapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusSeconds(30),
                now,
                30L,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(service.snapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.UNKNOWN, health.getStatus());
        assertEquals("awaiting-first-tick", health.getDetails().get("status"));
    }

    @Test
    void health_shouldBeDownWhenSchedulerHasNeverTickedBeyondStartupGrace() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot snapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(5),
                now,
                30L,
                0L,
                0,
                0L,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(service.snapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("scheduler-not-ticking", health.getDetails().get("status"));
    }

    @Test
    void health_shouldStayUpWhenSchedulerTickIsRecent() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot snapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(10),
                now,
                30L,
                5L,
                1,
                3L,
                2L,
                0L,
                1L,
                now.minusSeconds(20),
                null,
                null,
                UUID.randomUUID(),
                "RUNNING",
                null,
                null,
                ""
        );
        when(service.snapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("RUNNING", health.getDetails().get("lastRefreshedRunStatus"));
        assertEquals("", health.getDetails().get("lastRefreshAt"));
    }

    @Test
    void health_shouldBeDownWhenSchedulerTickIsStale() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot snapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(10),
                now,
                30L,
                5L,
                2,
                8L,
                3L,
                1L,
                4L,
                now.minusMinutes(3),
                now.minusMinutes(3),
                now.minusMinutes(3),
                UUID.randomUUID(),
                "FAILED",
                UUID.randomUUID(),
                "run_no_longer_refreshable",
                "market data unavailable"
        );
        when(service.snapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("run_no_longer_refreshable", health.getDetails().get("lastSkipReason"));
        assertEquals("market data unavailable", health.getDetails().get("lastError"));
    }
}
