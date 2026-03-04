package com.finance.core.observability;

import com.finance.core.config.OpsAlertingProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OpsAlertServiceTest {

    @Mock
    private OpsWebhookClient webhookClient;

    private SimpleMeterRegistry meterRegistry;
    private OpsAlertingProperties properties;
    private OpsAlertService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        properties = new OpsAlertingProperties();
        properties.setCooldown(Duration.ofMinutes(5));
        properties.setWebhookUrl("http://localhost:9999/ops");
        service = new OpsAlertService(meterRegistry, properties, webhookClient);
        ReflectionTestUtils.setField(service, "serviceName", "core-api-test");
    }

    @Test
    void publish_sameAlertWithinCooldown_shouldSuppressSecondWebhook() {
        service.publish("feed-latency", OpsAlertSeverity.WARNING, "warning-breach", "threshold breached", Map.of("count", 1));
        service.publish("feed-latency", OpsAlertSeverity.WARNING, "warning-breach", "threshold breached", Map.of("count", 2));

        verify(webhookClient, times(1)).post(eq("http://localhost:9999/ops"), anyMap());
        assertEquals(4, meterRegistry.getMeters().size());
    }

    @Test
    void publish_zeroCooldown_shouldNotSuppress() {
        properties.setCooldown(Duration.ZERO);

        service.publish("feed-latency", OpsAlertSeverity.WARNING, "warning-breach", "threshold breached", Map.of());
        service.publish("feed-latency", OpsAlertSeverity.WARNING, "warning-breach", "threshold breached", Map.of());

        verify(webhookClient, times(2)).post(eq("http://localhost:9999/ops"), anyMap());
    }

    @Test
    void publish_blankWebhook_shouldSkipWebhookAndStillRecordLogMetric() {
        properties.setWebhookUrl("");

        service.publish("shedlock", OpsAlertSeverity.WARNING, "stale-lock", "stale lock detected", Map.of("staleLocks", 2));

        verifyNoInteractions(webhookClient);
        assertEquals(1, meterRegistry.getMeters().size());
    }
}
