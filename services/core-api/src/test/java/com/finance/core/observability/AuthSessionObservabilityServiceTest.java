package com.finance.core.observability;

import com.finance.core.config.AuthObservabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthSessionObservabilityServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private AuthObservabilityProperties properties;
    private OpsAlertPublisher opsAlertPublisher;
    private AuthSessionObservabilityService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new AuthObservabilityProperties();
        properties.setRefreshInterval(Duration.ofSeconds(30));
        properties.setChurnWindow(Duration.ofMinutes(10));
        properties.setMinSamples(1);
        opsAlertPublisher = mock(OpsAlertPublisher.class);
        service = new AuthSessionObservabilityService(meterRegistry, properties, opsAlertPublisher);
        service.registerMeters();
    }

    @Test
    void refreshSnapshot_shouldPublishWarning_whenInvalidCountThresholdBreached() {
        properties.setMinSamples(2);
        properties.setWarningRefreshCount(500);
        properties.setCriticalRefreshCount(1000);
        properties.setWarningInvalidCount(1);
        properties.setCriticalInvalidCount(5);
        properties.setWarningInvalidRatio(0.40);
        properties.setCriticalInvalidRatio(0.80);

        service.recordRefreshSuccess();
        service.recordInvalidRefreshAttempt();
        AuthSessionChurnSnapshot snapshot = service.refreshSnapshot();

        assertEquals("WARNING", snapshot.alertState());
        verify(opsAlertPublisher, times(1)).publish(
                eq("auth-refresh-churn"),
                eq(OpsAlertSeverity.WARNING),
                eq("refresh-churn-breach"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshSnapshot_shouldEscalateToCriticalWithoutDuplicateAlertsWhenStateUnchanged() {
        properties.setWarningRefreshCount(1);
        properties.setCriticalRefreshCount(2);
        properties.setWarningInvalidCount(500);
        properties.setCriticalInvalidCount(1000);
        properties.setWarningInvalidRatio(0.99);
        properties.setCriticalInvalidRatio(1.0);

        service.recordRefreshSuccess();
        AuthSessionChurnSnapshot first = service.refreshSnapshot();

        service.recordRefreshSuccess();
        AuthSessionChurnSnapshot second = service.refreshSnapshot();

        AuthSessionChurnSnapshot third = service.refreshSnapshot();

        assertEquals("WARNING", first.alertState());
        assertEquals("CRITICAL", second.alertState());
        assertEquals("CRITICAL", third.alertState());

        verify(opsAlertPublisher, times(1)).publish(
                eq("auth-refresh-churn"),
                eq(OpsAlertSeverity.WARNING),
                eq("refresh-churn-breach"),
                anyString(),
                anyMap()
        );
        verify(opsAlertPublisher, times(1)).publish(
                eq("auth-refresh-churn"),
                eq(OpsAlertSeverity.CRITICAL),
                eq("refresh-churn-breach"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshSnapshot_shouldPublishRecoveryAlert_whenStateReturnsToNone() {
        properties.setMinSamples(2);
        properties.setWarningRefreshCount(500);
        properties.setCriticalRefreshCount(1000);
        properties.setWarningInvalidCount(1);
        properties.setCriticalInvalidCount(5);
        properties.setWarningInvalidRatio(0.40);
        properties.setCriticalInvalidRatio(0.80);
        properties.setAlertOnRecovery(true);

        service.recordRefreshSuccess();
        service.recordInvalidRefreshAttempt();
        AuthSessionChurnSnapshot warningSnapshot = service.refreshSnapshot();
        assertEquals("WARNING", warningSnapshot.alertState());

        properties.setMinSamples(100);
        AuthSessionChurnSnapshot recoveredSnapshot = service.refreshSnapshot();
        assertEquals("NONE", recoveredSnapshot.alertState());

        verify(opsAlertPublisher, times(1)).publish(
                eq("auth-refresh-churn"),
                eq(OpsAlertSeverity.WARNING),
                eq("refresh-churn-recovered"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void refreshSnapshot_usesLocaleRootForCriticalTransitionTags() {
        properties.setWarningRefreshCount(1);
        properties.setCriticalRefreshCount(2);
        properties.setWarningInvalidCount(500);
        properties.setCriticalInvalidCount(1000);
        properties.setWarningInvalidRatio(0.99);
        properties.setCriticalInvalidRatio(1.0);

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            service.recordRefreshSuccess();
            service.refreshSnapshot();
            service.recordRefreshSuccess();
            service.refreshSnapshot();

            assertNotNull(meterRegistry.find("app.auth.refresh.state.transitions")
                    .tags("from", "warning", "to", "critical")
                    .counter());
            assertEquals(1.0, meterRegistry.find("app.auth.refresh.state.transitions")
                    .tags("from", "warning", "to", "critical")
                    .counter()
                    .count());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
