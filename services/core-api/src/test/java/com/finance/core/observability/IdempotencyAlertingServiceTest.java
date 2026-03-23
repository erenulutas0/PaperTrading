package com.finance.core.observability;

import com.finance.core.config.IdempotencyProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyAlertingServiceTest {

    private IdempotencyObservabilityService observabilityService;
    private IdempotencyProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private OpsAlertPublisher opsAlertPublisher;
    private IdempotencyAlertingService service;

    @BeforeEach
    void setUp() {
        observabilityService = mock(IdempotencyObservabilityService.class);
        properties = new IdempotencyProperties();
        properties.setCleanupInterval(Duration.ofMinutes(1));
        meterRegistry = new SimpleMeterRegistry();
        opsAlertPublisher = mock(OpsAlertPublisher.class);
        service = new IdempotencyAlertingService(observabilityService, properties, meterRegistry, opsAlertPublisher);
        service.registerMeters();
    }

    @Test
    void refreshAlertState_shouldPublishWarningOnceWhenCleanupLagStarts() {
        IdempotencySnapshot staleSnapshot = snapshot(1, 180.0, null);
        when(observabilityService.refreshSnapshot()).thenReturn(staleSnapshot);

        service.refreshAlertState();
        service.refreshAlertState();

        assertEquals("WARNING", service.currentAlertState());
        verify(opsAlertPublisher, times(1)).publish(
                eq("idempotency-cleanup"),
                eq(OpsAlertSeverity.WARNING),
                eq("cleanup-health-degraded"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshAlertState_shouldPublishRecoveryWhenCleanupLagClears() {
        IdempotencySnapshot staleSnapshot = snapshot(1, 180.0, null);
        IdempotencySnapshot healthySnapshot = snapshot(0, null, null);
        when(observabilityService.refreshSnapshot()).thenReturn(staleSnapshot, healthySnapshot);

        service.refreshAlertState();
        service.refreshAlertState();

        assertEquals("NONE", service.currentAlertState());
        verify(opsAlertPublisher, times(1)).publish(
                eq("idempotency-cleanup"),
                eq(OpsAlertSeverity.WARNING),
                eq("cleanup-health-degraded"),
                anyString(),
                anyMap()
        );
        verify(opsAlertPublisher, times(1)).publish(
                eq("idempotency-cleanup"),
                eq(OpsAlertSeverity.WARNING),
                eq("cleanup-health-recovered"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshAlertState_shouldWarnWhenSnapshotFails() {
        IdempotencySnapshot errorSnapshot = snapshot(0, null, "query failed");
        when(observabilityService.refreshSnapshot()).thenReturn(errorSnapshot);

        service.refreshAlertState();

        assertEquals("WARNING", service.currentAlertState());
        verify(opsAlertPublisher, times(1)).publish(
                eq("idempotency-cleanup"),
                eq(OpsAlertSeverity.WARNING),
                eq("cleanup-health-degraded"),
                anyString(),
                anyMap()
        );
    }

    private IdempotencySnapshot snapshot(long expiredRecords, Double oldestExpiredAgeSeconds, String error) {
        return new IdempotencySnapshot(
                LocalDateTime.now(),
                true,
                86400,
                60,
                4,
                0,
                4,
                expiredRecords,
                oldestExpiredAgeSeconds,
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
                LocalDateTime.now(),
                0,
                error
        );
    }
}
