package com.finance.core.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.alerting.enabled", havingValue = "false")
public class NoOpOpsAlertPublisher implements OpsAlertPublisher {

    @Override
    public void publish(String component, OpsAlertSeverity severity, String alertKey, String message, Map<String, Object> details) {
        // no-op fallback when alerting is disabled
    }
}
