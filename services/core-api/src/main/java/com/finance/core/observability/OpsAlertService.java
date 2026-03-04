package com.finance.core.observability;

import com.finance.core.config.OpsAlertingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.alerting.enabled", havingValue = "true", matchIfMissing = true)
public class OpsAlertService implements OpsAlertPublisher {

    private static final String ALERT_METRIC = "app.ops.alerts.total";
    private static final String LOG_CHANNEL = "log";
    private static final String WEBHOOK_CHANNEL = "webhook";
    private static final String RESULT_SENT = "sent";
    private static final String RESULT_SUPPRESSED = "suppressed";
    private static final String RESULT_FAILED = "failed";

    private final MeterRegistry meterRegistry;
    private final OpsAlertingProperties properties;
    private final OpsWebhookClient webhookClient;

    @Value("${spring.application.name:core-api}")
    private String serviceName;

    private final ConcurrentHashMap<String, Instant> lastSentByKey = new ConcurrentHashMap<>();

    @Override
    public void publish(String component, OpsAlertSeverity severity, String alertKey, String message, Map<String, Object> details) {
        String dedupeKey = component + ":" + severity.name() + ":" + alertKey;
        Instant now = Instant.now();
        if (isSuppressed(dedupeKey, now, properties.getCooldown())) {
            increment(component, severity, LOG_CHANNEL, RESULT_SUPPRESSED);
            increment(component, severity, WEBHOOK_CHANNEL, RESULT_SUPPRESSED);
            return;
        }

        logAlert(component, severity, message, details);
        increment(component, severity, LOG_CHANNEL, RESULT_SENT);

        if (!StringUtils.hasText(properties.getWebhookUrl())) {
            return;
        }

        try {
            webhookClient.post(properties.getWebhookUrl(), buildPayload(component, severity, alertKey, message, details, now));
            increment(component, severity, WEBHOOK_CHANNEL, RESULT_SENT);
        } catch (Exception ex) {
            increment(component, severity, WEBHOOK_CHANNEL, RESULT_FAILED);
            log.error("Ops webhook publish failed for component={} severity={} key={}", component, severity, alertKey, ex);
        }
    }

    private void logAlert(String component, OpsAlertSeverity severity, String message, Map<String, Object> details) {
        if (severity == OpsAlertSeverity.CRITICAL) {
            log.error("Ops alert [{}] {} details={}", component, message, details);
            return;
        }
        log.warn("Ops alert [{}] {} details={}", component, message, details);
    }

    private Map<String, Object> buildPayload(String component,
                                             OpsAlertSeverity severity,
                                             String alertKey,
                                             String message,
                                             Map<String, Object> details,
                                             Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service", serviceName);
        payload.put("component", component);
        payload.put("severity", severity.name());
        payload.put("alertKey", alertKey);
        payload.put("message", message);
        payload.put("timestamp", now.toString());
        payload.put("details", details == null ? Map.of() : details);
        return payload;
    }

    private boolean isSuppressed(String dedupeKey, Instant now, Duration cooldown) {
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            lastSentByKey.put(dedupeKey, now);
            return false;
        }

        AtomicBoolean suppressed = new AtomicBoolean(false);
        lastSentByKey.compute(dedupeKey, (key, previous) -> {
            if (previous != null && now.isBefore(previous.plus(cooldown))) {
                suppressed.set(true);
                return previous;
            }
            return now;
        });
        return suppressed.get();
    }

    private void increment(String component, OpsAlertSeverity severity, String channel, String result) {
        meterRegistry.counter(
                        ALERT_METRIC,
                        "component", component,
                        "severity", severity.name().toLowerCase(),
                        "channel", channel,
                        "result", result)
                .increment();
    }
}
