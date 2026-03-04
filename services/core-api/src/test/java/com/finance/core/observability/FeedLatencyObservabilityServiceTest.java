package com.finance.core.observability;

import com.finance.core.config.FeedObservabilityProperties;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeedLatencyObservabilityServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private FeedObservabilityProperties properties;
    private OpsAlertPublisher opsAlertPublisher;
    private FeedLatencyObservabilityService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new FeedObservabilityProperties();
        properties.setUris(List.of("/api/v1/feed/global"));
        properties.setRefreshInterval(Duration.ofSeconds(30));
        properties.setMinSamples(1);
        opsAlertPublisher = mock(OpsAlertPublisher.class);
        service = new FeedLatencyObservabilityService(meterRegistry, properties, opsAlertPublisher);
        service.registerMeters();
    }

    @Test
    void refreshSnapshot_shouldPublishCriticalAlert_whenCriticalThresholdBreached() {
        properties.setWarningP95Ms(0.0);
        properties.setWarningP99Ms(0.0);
        properties.setCriticalP99Ms(0.0);

        Timer timer = Timer.builder("http.server.requests")
                .tag("uri", "/api/v1/feed/global")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        timer.record(Duration.ofMillis(12));

        service.refreshSnapshot();

        verify(opsAlertPublisher).publish(
                eq("feed-latency"),
                eq(OpsAlertSeverity.CRITICAL),
                eq("critical-breach"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshSnapshot_shouldPublishWarningAlert_whenOnlyWarningThresholdBreached() {
        properties.setWarningP95Ms(0.0);
        properties.setWarningP99Ms(0.0);
        properties.setCriticalP99Ms(10_000.0);

        Timer timer = Timer.builder("http.server.requests")
                .tag("uri", "/api/v1/feed/global")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        timer.record(Duration.ofMillis(10));

        service.refreshSnapshot();

        verify(opsAlertPublisher).publish(
                eq("feed-latency"),
                eq(OpsAlertSeverity.WARNING),
                eq("warning-breach"),
                anyString(),
                anyMap()
        );
    }
}
