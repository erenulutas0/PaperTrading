package com.finance.core.observability;

import com.finance.core.config.ShedLockObservabilityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShedLockHealthIndicatorTest {

    private ShedLockObservabilityService shedLockObservabilityService;
    private ShedLockObservabilityProperties properties;
    private ShedLockHealthIndicator shedLockHealthIndicator;

    @BeforeEach
    void setUp() {
        shedLockObservabilityService = mock(ShedLockObservabilityService.class);
        properties = new ShedLockObservabilityProperties();
        properties.setAlertStaleLockCount(1);
        shedLockHealthIndicator = new ShedLockHealthIndicator(shedLockObservabilityService, properties);
    }

    @Test
    void shouldReturnUpWhenNoStaleLocks() {
        ShedLockSnapshot snapshot = new ShedLockSnapshot(
                LocalDateTime.now(),
                2,
                0,
                12.0,
                30.0,
                900,
                List.of(),
                null
        );
        when(shedLockObservabilityService.getLatestSnapshot()).thenReturn(snapshot);

        Health health = shedLockHealthIndicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void shouldReturnDownWhenStaleLocksReachThreshold() {
        ShedLockSnapshot snapshot = new ShedLockSnapshot(
                LocalDateTime.now(),
                2,
                1,
                1200.0,
                30.0,
                900,
                List.of(),
                null
        );
        when(shedLockObservabilityService.getLatestSnapshot()).thenReturn(snapshot);

        Health health = shedLockHealthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void shouldReturnDownWhenSnapshotUnavailable() {
        ShedLockSnapshot snapshot = new ShedLockSnapshot(
                LocalDateTime.now(),
                0,
                0,
                0,
                0,
                900,
                List.of(),
                "query failed"
        );
        when(shedLockObservabilityService.getLatestSnapshot()).thenReturn(snapshot);

        Health health = shedLockHealthIndicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}
