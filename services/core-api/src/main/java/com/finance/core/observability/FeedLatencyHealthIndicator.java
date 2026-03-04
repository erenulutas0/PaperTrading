package com.finance.core.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("feedLatency")
@ConditionalOnProperty(name = "app.feed.observability.enabled", havingValue = "true", matchIfMissing = true)
public class FeedLatencyHealthIndicator implements HealthIndicator {

    private final FeedLatencyObservabilityService service;

    public FeedLatencyHealthIndicator(FeedLatencyObservabilityService service) {
        this.service = service;
    }

    @Override
    public Health health() {
        FeedLatencySnapshot snapshot = service.refreshSnapshot();
        if (!snapshot.available()) {
            return Health.down()
                    .withDetail("error", snapshot.error())
                    .build();
        }

        Health.Builder builder = snapshot.hasCriticalBreach() ? Health.down() : Health.up();
        return builder
                .withDetail("warningBreaches", snapshot.warningBreaches())
                .withDetail("criticalBreaches", snapshot.criticalBreaches())
                .withDetail("warningP95Ms", snapshot.warningP95Ms())
                .withDetail("warningP99Ms", snapshot.warningP99Ms())
                .withDetail("criticalP99Ms", snapshot.criticalP99Ms())
                .withDetail("monitoredUris", snapshot.points().size())
                .build();
    }
}
