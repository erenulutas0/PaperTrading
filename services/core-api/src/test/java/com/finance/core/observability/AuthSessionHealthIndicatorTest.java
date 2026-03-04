package com.finance.core.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthSessionHealthIndicatorTest {

    private AuthSessionObservabilityService service;
    private AuthSessionHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        service = mock(AuthSessionObservabilityService.class);
        indicator = new AuthSessionHealthIndicator(service);
    }

    @Test
    void shouldReturnUpWhenNoCriticalBreach() {
        AuthSessionChurnSnapshot snapshot = new AuthSessionChurnSnapshot(
                LocalDateTime.now(),
                600,
                20,
                300,
                800,
                20,
                50,
                0.10,
                0.25,
                80,
                3,
                83,
                0.0361,
                false,
                false,
                "NONE",
                null
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void shouldReturnDownWhenCriticalBreachExists() {
        AuthSessionChurnSnapshot snapshot = new AuthSessionChurnSnapshot(
                LocalDateTime.now(),
                600,
                20,
                300,
                800,
                20,
                50,
                0.10,
                0.25,
                900,
                80,
                980,
                0.0816,
                true,
                true,
                "CRITICAL",
                null
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }

    @Test
    void shouldReturnDownWhenSnapshotUnavailable() {
        AuthSessionChurnSnapshot snapshot = new AuthSessionChurnSnapshot(
                LocalDateTime.now(),
                600,
                20,
                300,
                800,
                20,
                50,
                0.10,
                0.25,
                0,
                0,
                0,
                0.0,
                false,
                false,
                "NONE",
                "unavailable"
        );
        when(service.refreshSnapshot()).thenReturn(snapshot);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
    }
}
