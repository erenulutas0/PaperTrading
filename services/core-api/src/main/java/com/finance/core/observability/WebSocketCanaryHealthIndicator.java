package com.finance.core.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("websocketCanary")
@ConditionalOnProperty(name = "app.websocket.canary.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketCanaryHealthIndicator implements HealthIndicator {

    private final WebSocketCanaryService webSocketCanaryService;

    public WebSocketCanaryHealthIndicator(WebSocketCanaryService webSocketCanaryService) {
        this.webSocketCanaryService = webSocketCanaryService;
    }

    @Override
    public Health health() {
        WebSocketCanarySnapshot snapshot = webSocketCanaryService.getLatestSnapshot();
        if (snapshot.checkedAt() == null) {
            return Health.unknown()
                    .withDetail("status", "not-run-yet")
                    .withDetail("consecutiveFailures", snapshot.consecutiveFailures())
                    .build();
        }
        boolean critical = "CRITICAL".equalsIgnoreCase(snapshot.alertState());
        Health.Builder builder = critical ? Health.down() : Health.up();
        return builder
                .withDetail("checkedAt", snapshot.checkedAt())
                .withDetail("alertState", snapshot.alertState())
                .withDetail("consecutiveFailures", snapshot.consecutiveFailures())
                .withDetail("criticalFailureThreshold", snapshot.criticalFailureThreshold())
                .withDetail("latencyMs", snapshot.latencyMs())
                .withDetail("wsUrl", snapshot.wsUrl())
                .withDetail("topicReceived", snapshot.topicReceived())
                .withDetail("userQueueReceived", snapshot.userQueueReceived())
                .withDetail("error", snapshot.error() == null ? "" : snapshot.error())
                .build();
    }
}
