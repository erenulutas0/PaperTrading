package com.finance.core.observability;

import com.finance.core.config.StrategyBotForwardTestObservabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StrategyBotForwardTestAlertingServiceTest {

    private StrategyBotForwardTestObservabilityService observabilityService;
    private StrategyBotForwardTestObservabilityProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private OpsAlertPublisher opsAlertPublisher;
    private StrategyBotForwardTestAlertingService service;

    @BeforeEach
    void setUp() {
        observabilityService = mock(StrategyBotForwardTestObservabilityService.class);
        properties = new StrategyBotForwardTestObservabilityProperties();
        meterRegistry = new SimpleMeterRegistry();
        opsAlertPublisher = mock(OpsAlertPublisher.class);
        service = new StrategyBotForwardTestAlertingService(observabilityService, properties, meterRegistry, opsAlertPublisher);
        service.registerMeters();
    }

    @Test
    void refreshAlertState_shouldPublishWarningOnceWhenSchedulerTurnsStale() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot staleSnapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(10),
                now,
                30L,
                5L,
                1,
                3L,
                1L,
                1L,
                5L,
                now.minusMinutes(3),
                now.minusMinutes(3),
                now.minusMinutes(3),
                null,
                "",
                null,
                "",
                ""
        );
        when(observabilityService.snapshot()).thenReturn(staleSnapshot);

        service.refreshAlertState();
        service.refreshAlertState();

        verify(opsAlertPublisher, times(1)).publish(
                eq("strategy-bot-forward-test-scheduler"),
                eq(OpsAlertSeverity.WARNING),
                eq("scheduler-stale"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshAlertState_shouldPublishRecoveryWhenSchedulerRecovers() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot staleSnapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(10),
                now,
                30L,
                5L,
                1,
                3L,
                1L,
                1L,
                5L,
                now.minusMinutes(3),
                now.minusMinutes(3),
                now.minusMinutes(3),
                null,
                "",
                null,
                "",
                ""
        );
        StrategyBotForwardTestSchedulerSnapshot healthySnapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(10),
                now.plusSeconds(10),
                30L,
                6L,
                1,
                4L,
                2L,
                1L,
                5L,
                now.plusSeconds(5),
                now.plusSeconds(5),
                null,
                null,
                "RUNNING",
                null,
                "",
                ""
        );
        when(observabilityService.snapshot()).thenReturn(staleSnapshot, healthySnapshot);

        service.refreshAlertState();
        service.refreshAlertState();

        verify(opsAlertPublisher, times(1)).publish(
                eq("strategy-bot-forward-test-scheduler"),
                eq(OpsAlertSeverity.WARNING),
                eq("scheduler-stale"),
                anyString(),
                anyMap()
        );
        verify(opsAlertPublisher, times(1)).publish(
                eq("strategy-bot-forward-test-scheduler"),
                eq(OpsAlertSeverity.WARNING),
                eq("scheduler-recovered"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshAlertState_shouldNotAlertBeforeFirstTickInsideStartupGrace() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot startupSnapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusSeconds(20),
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
        when(observabilityService.snapshot()).thenReturn(startupSnapshot);

        service.refreshAlertState();

        verify(opsAlertPublisher, never()).publish(
                eq("strategy-bot-forward-test-scheduler"),
                eq(OpsAlertSeverity.WARNING),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void statusSnapshot_shouldExposeResolvedAlertFields() {
        LocalDateTime now = LocalDateTime.now();
        StrategyBotForwardTestSchedulerSnapshot healthySnapshot = new StrategyBotForwardTestSchedulerSnapshot(
                now.minusMinutes(10),
                now,
                30L,
                4L,
                2,
                2L,
                2L,
                0L,
                0L,
                now.minusSeconds(5),
                null,
                null,
                null,
                "RUNNING",
                null,
                null,
                ""
        );
        when(observabilityService.snapshot()).thenReturn(healthySnapshot);

        StrategyBotForwardTestStatusSnapshot statusSnapshot = service.statusSnapshot();

        assertEquals("NONE", statusSnapshot.alertState());
        assertEquals(properties.normalizedStaleThreshold().toSeconds(), statusSnapshot.staleThresholdSeconds());
        assertTrue(statusSnapshot.lastTickAgeSeconds() >= 0.0);
        assertEquals(4L, statusSnapshot.scheduledTickCount());
    }
}
