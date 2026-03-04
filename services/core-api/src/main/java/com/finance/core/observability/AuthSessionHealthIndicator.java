package com.finance.core.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("authSessions")
@ConditionalOnProperty(name = "app.auth.observability.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionHealthIndicator implements HealthIndicator {

    private final AuthSessionObservabilityService service;

    public AuthSessionHealthIndicator(AuthSessionObservabilityService service) {
        this.service = service;
    }

    @Override
    public Health health() {
        AuthSessionChurnSnapshot snapshot = service.refreshSnapshot();
        if (!snapshot.available()) {
            return Health.down()
                    .withDetail("error", snapshot.error())
                    .build();
        }

        Health.Builder builder = snapshot.hasCriticalBreach() ? Health.down() : Health.up();
        return builder
                .withDetail("alertState", snapshot.alertState())
                .withDetail("windowSeconds", snapshot.windowSeconds())
                .withDetail("refreshSuccessCount", snapshot.refreshSuccessCount())
                .withDetail("invalidRefreshCount", snapshot.invalidRefreshCount())
                .withDetail("invalidRefreshRatio", snapshot.invalidRefreshRatio())
                .build();
    }
}
