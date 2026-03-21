package com.finance.core.observability;

import com.finance.core.config.WebSocketCanaryProperties;
import com.finance.core.config.WebSocketRuntimeProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class WebSocketCanaryServiceTest {

    private WebSocketCanaryProperties properties;
    private WebSocketRuntimeProperties runtimeProperties;
    private WebSocketCanaryClient canaryClient;
    private OpsAlertPublisher opsAlertPublisher;
    private SimpleMeterRegistry meterRegistry;
    private WebSocketCanaryService service;

    @BeforeEach
    void setUp() {
        properties = new WebSocketCanaryProperties();
        properties.setWarningConsecutiveFailureThreshold(2);
        properties.setCriticalFailureThreshold(2);
        properties.setRecoverySuccessThreshold(2);
        properties.setMinWindowSamples(5);
        properties.setBaseUrl("http://localhost:8080");
        properties.setWsUrl("ws://localhost:8080/ws");
        runtimeProperties = new WebSocketRuntimeProperties();
        canaryClient = mock(WebSocketCanaryClient.class);
        opsAlertPublisher = mock(OpsAlertPublisher.class);
        meterRegistry = new SimpleMeterRegistry();

        service = new WebSocketCanaryService(properties, runtimeProperties, canaryClient, meterRegistry, opsAlertPublisher);
        service.registerMeters();
    }

    @Test
    void runCanaryProbe_successShouldResetFailuresAndUpdateSnapshot() {
        when(canaryClient.probe(any())).thenReturn(new WebSocketCanaryProbeResult(true, true, 40, null));

        WebSocketCanarySnapshot snapshot = service.runCanaryProbe();

        assertTrue(snapshot.success());
        assertEquals(0, snapshot.consecutiveFailures());
        assertEquals("NONE", snapshot.alertState());

        Counter successCounter = meterRegistry.find("app.websocket.canary.runs.total")
                .tags("result", "success")
                .counter();
        assertEquals(1.0, successCounter.count());
        verifyNoInteractions(opsAlertPublisher);
    }

    @Test
    void runCanaryProbe_consecutiveFailuresShouldEscalateSeverityWithoutSpam() {
        properties.setWarningConsecutiveFailureThreshold(2);
        properties.setCriticalFailureThreshold(3);
        when(canaryClient.probe(any())).thenReturn(new WebSocketCanaryProbeResult(false, true, 15, "timeout"));

        WebSocketCanarySnapshot first = service.runCanaryProbe();
        WebSocketCanarySnapshot second = service.runCanaryProbe();
        WebSocketCanarySnapshot third = service.runCanaryProbe();
        WebSocketCanarySnapshot fourth = service.runCanaryProbe();

        assertEquals(1, first.consecutiveFailures());
        assertEquals(2, second.consecutiveFailures());
        assertEquals(3, third.consecutiveFailures());
        assertEquals(4, fourth.consecutiveFailures());
        assertEquals("CRITICAL", fourth.alertState());

        verify(opsAlertPublisher, times(1)).publish(
                eq("websocket-canary"),
                eq(OpsAlertSeverity.WARNING),
                eq("probe-failed"),
                anyString(),
                anyMap()
        );
        verify(opsAlertPublisher, times(1)).publish(
                eq("websocket-canary"),
                eq(OpsAlertSeverity.CRITICAL),
                eq("probe-failed"),
                anyString(),
                anyMap()
        );
        verify(opsAlertPublisher, never()).publish(
                eq("websocket-canary"),
                eq(OpsAlertSeverity.WARNING),
                eq("probe-recovered"),
                anyString(),
                anyMap()
        );

        Counter failCounter = meterRegistry.find("app.websocket.canary.runs.total")
                .tags("result", "failed")
                .counter();
        assertEquals(4.0, failCounter.count());
    }

    @Test
    void runCanaryProbe_shouldPublishRecoveryAfterConfiguredSuccessThreshold() {
        properties.setWarningConsecutiveFailureThreshold(1);
        properties.setCriticalFailureThreshold(3);
        properties.setRecoverySuccessThreshold(2);
        properties.setMinWindowSamples(5);

        when(canaryClient.probe(any()))
                .thenReturn(new WebSocketCanaryProbeResult(false, true, 20, "timeout"))
                .thenReturn(new WebSocketCanaryProbeResult(true, true, 10, null))
                .thenReturn(new WebSocketCanaryProbeResult(true, true, 10, null));

        WebSocketCanarySnapshot first = service.runCanaryProbe();
        WebSocketCanarySnapshot second = service.runCanaryProbe();
        WebSocketCanarySnapshot third = service.runCanaryProbe();

        assertEquals("WARNING", first.alertState());
        assertEquals("WARNING", second.alertState());
        assertEquals("NONE", third.alertState());

        verify(opsAlertPublisher, times(1)).publish(
                eq("websocket-canary"),
                eq(OpsAlertSeverity.WARNING),
                eq("probe-failed"),
                anyString(),
                anyMap()
        );
        verify(opsAlertPublisher, times(1)).publish(
                eq("websocket-canary"),
                eq(OpsAlertSeverity.WARNING),
                eq("probe-recovered"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void runCanaryProbe_whenClientThrows_shouldReturnFailedSnapshotInsteadOfThrowing(CapturedOutput output) {
        properties.setWarningConsecutiveFailureThreshold(1);
        when(canaryClient.probe(any())).thenThrow(new IllegalStateException("broker unavailable"));

        WebSocketCanarySnapshot snapshot = service.runCanaryProbe();

        assertEquals(false, snapshot.success());
        assertEquals(1, snapshot.consecutiveFailures());
        assertEquals("broker unavailable", snapshot.error());
        assertEquals("WARNING", snapshot.alertState());

        Counter failCounter = meterRegistry.find("app.websocket.canary.runs.total")
                .tags("result", "failed")
                .counter();
        assertEquals(1.0, failCounter.count());
        verify(opsAlertPublisher, times(1)).publish(
                eq("websocket-canary"),
                eq(OpsAlertSeverity.WARNING),
                eq("probe-failed"),
                anyString(),
                anyMap()
        );
        assertTrue(output.getOut().contains("WebSocket canary probe raised exception: broker unavailable"));
        assertTrue(!output.getOut().contains("java.lang.IllegalStateException: broker unavailable"));
    }
}
