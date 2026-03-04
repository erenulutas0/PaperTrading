package com.finance.core.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnMissingBean(OpsAlertPublisher.class)
public class NoOpOpsAlertPublisher implements OpsAlertPublisher {

    @Override
    public void publish(String component, OpsAlertSeverity severity, String alertKey, String message, Map<String, Object> details) {
        // no-op fallback when alerting is disabled
    }
}
