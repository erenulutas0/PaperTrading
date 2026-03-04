package com.finance.core.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedLatencyHealthIndicatorTest {

    private FeedLatencyObservabilityService service;
    private FeedLatencyHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        service = mock(FeedLatencyObservabilityService.class);
        indicator = new FeedLatencyHealthIndicator(service);
    }

    @Test
    void shouldReturnUpWhenNoCriticalBreach() {
        FeedLatencySnapshot snapshot = new FeedLatencySnapshot(
                LocalDateTime.now(),
                100,
                40,
                100,
                150,
                1,
                0,
                List.of(),
                null
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void shouldReturnDownWhenCriticalBreachExists() {
        FeedLatencySnapshot snapshot = new FeedLatencySnapshot(
                LocalDateTime.now(),
                100,
                40,
                100,
                150,
                2,
                1,
                List.of(),
                null
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void shouldReturnDownWhenSnapshotUnavailable() {
        FeedLatencySnapshot snapshot = new FeedLatencySnapshot(
                LocalDateTime.now(),
                100,
                40,
                100,
                150,
                0,
                0,
                List.of(),
                "metrics unavailable"
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}
