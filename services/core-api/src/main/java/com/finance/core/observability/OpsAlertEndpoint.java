package com.finance.core.observability;

import com.finance.core.config.OpsAlertingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@Endpoint(id = "opsalerts")
public class OpsAlertEndpoint {

    private static final String ALERT_METRIC = "app.ops.alerts.total";
    private static final String DEFAULT_COMPONENT = "ops-manual";
    private static final String DEFAULT_MESSAGE = "Manual ops alert validation";

    private final OpsAlertPublisher opsAlertPublisher;
    private final OpsAlertingProperties properties;
    private final MeterRegistry meterRegistry;

    public OpsAlertEndpoint(
            OpsAlertPublisher opsAlertPublisher,
            OpsAlertingProperties properties,
            MeterRegistry meterRegistry) {
        this.opsAlertPublisher = opsAlertPublisher;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @ReadOperation
    public Map<String, Object> opsAlertStatus() {
        Map<String, Object> payload = basePayload();
        payload.put("cooldownSeconds", properties.getCooldown() == null ? 0 : properties.getCooldown().getSeconds());
        payload.put("totalAlertCount", sumCounters(null, null));
        payload.put("logSentCount", sumCounters("log", "sent"));
        payload.put("logSuppressedCount", sumCounters("log", "suppressed"));
        payload.put("webhookSentCount", sumCounters("webhook", "sent"));
        payload.put("webhookSuppressedCount", sumCounters("webhook", "suppressed"));
        payload.put("webhookFailedCount", sumCounters("webhook", "failed"));
        return payload;
    }

    @WriteOperation
    public Map<String, Object> publishValidationAlert(@Nullable String component,
                                                      @Nullable String severity,
                                                      @Nullable String alertKey,
                                                      @Nullable String message) {
        Map<String, Object> payload = basePayload();
        OpsAlertSeverity resolvedSeverity = resolveSeverity(severity);
        if (resolvedSeverity == null) {
            payload.put("accepted", false);
            payload.put("error", "invalid_severity");
            payload.put("providedSeverity", severity);
            return payload;
        }

        String resolvedComponent = StringUtils.hasText(component) ? component.trim() : DEFAULT_COMPONENT;
        String resolvedAlertKey = StringUtils.hasText(alertKey)
                ? alertKey.trim()
                : "manual-validation-" + UUID.randomUUID();
        String resolvedMessage = StringUtils.hasText(message) ? message.trim() : DEFAULT_MESSAGE;

        payload.put("accepted", properties.isEnabled());
        payload.put("checkedAt", OffsetDateTime.now().toString());
        payload.put("component", resolvedComponent);
        payload.put("severity", resolvedSeverity.name());
        payload.put("alertKey", resolvedAlertKey);
        payload.put("message", resolvedMessage);

        if (!properties.isEnabled()) {
            payload.put("error", "alerting_disabled");
            return payload;
        }

        opsAlertPublisher.publish(
                resolvedComponent,
                resolvedSeverity,
                resolvedAlertKey,
                resolvedMessage,
                Map.of(
                        "source", "actuator",
                        "webhookConfigured", hasWebhookUrl()
                )
        );
        payload.put("published", true);
        return payload;
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", properties.isEnabled());
        payload.put("webhookConfigured", hasWebhookUrl());
        return payload;
    }

    private boolean hasWebhookUrl() {
        return StringUtils.hasText(properties.getWebhookUrl());
    }

    private double sumCounters(@Nullable String channel, @Nullable String result) {
        return meterRegistry.find(ALERT_METRIC)
                .counters()
                .stream()
                .filter(counter -> matchesTag(counter, "channel", channel))
                .filter(counter -> matchesTag(counter, "result", result))
                .mapToDouble(Counter::count)
                .sum();
    }

    private boolean matchesTag(Counter counter, String tagKey, @Nullable String expectedValue) {
        if (!StringUtils.hasText(expectedValue)) {
            return true;
        }
        String actualValue = counter.getId().getTag(tagKey);
        return expectedValue.equalsIgnoreCase(actualValue);
    }

    @Nullable
    private OpsAlertSeverity resolveSeverity(@Nullable String severity) {
        if (!StringUtils.hasText(severity)) {
            return OpsAlertSeverity.WARNING;
        }
        try {
            return OpsAlertSeverity.valueOf(severity.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
