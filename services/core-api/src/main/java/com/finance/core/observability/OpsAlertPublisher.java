package com.finance.core.observability;

import java.util.Map;

public interface OpsAlertPublisher {

    void publish(String component, OpsAlertSeverity severity, String alertKey, String message, Map<String, Object> details);
}
