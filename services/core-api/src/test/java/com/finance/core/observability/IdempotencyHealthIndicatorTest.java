package com.finance.core.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyHealthIndicatorTest {

    private IdempotencyObservabilityService service;
    private IdempotencyHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        service = mock(IdempotencyObservabilityService.class);
        indicator = new IdempotencyHealthIndicator(service);
    }

    @Test
    void health_shouldBeUpWhenExpiredBacklogIsWithinCleanupWindow() {
        IdempotencySnapshot snapshot = new IdempotencySnapshot(
                LocalDateTime.now(),
                true,
                86400,
                1800,
                5,
                1,
                4,
                1,
                120.0,
                3,
                2,
                1,
                0,
                2,
                1,
                0,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                LocalDateTime.now(),
                1,
                null
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("healthy", health.getDetails().get("status"));
    }

    @Test
    void health_shouldBeDownWhenExpiredBacklogOutrunsCleanupWindow() {
        IdempotencySnapshot snapshot = new IdempotencySnapshot(
                LocalDateTime.now(),
                true,
                86400,
                60,
                4,
                0,
                4,
                2,
                180.0,
                1,
                1,
                0,
                0,
                1,
                0,
                0,
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now().minusMinutes(2),
                0,
                null
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("cleanup-lagging", health.getDetails().get("status"));
    }

    @Test
    void health_shouldBeDownWhenSnapshotErrors() {
        IdempotencySnapshot snapshot = new IdempotencySnapshot(
                LocalDateTime.now(),
                true,
                86400,
                1800,
                0,
                0,
                0,
                0,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                "query failed"
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("snapshot-unavailable", health.getDetails().get("status"));
        assertEquals("query failed", health.getDetails().get("error"));
    }
}
