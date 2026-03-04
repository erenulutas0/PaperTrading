package com.finance.core.observability;

import com.finance.core.config.ShedLockObservabilityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("shedlock")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.shedlock.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ShedLockHealthIndicator implements HealthIndicator {

    private final ShedLockObservabilityService shedLockObservabilityService;
    private final ShedLockObservabilityProperties properties;

    @Override
    public Health health() {
        ShedLockSnapshot snapshot = shedLockObservabilityService.getLatestSnapshot();

        if (!snapshot.available()) {
            return Health.down()
                    .withDetail("error", snapshot.error())
                    .build();
        }

        boolean staleLockAlert = snapshot.staleLocks() >= properties.getAlertStaleLockCount();
        Health.Builder builder = staleLockAlert ? Health.down() : Health.up();

        return builder
                .withDetail("activeLocks", snapshot.activeLocks())
                .withDetail("staleLocks", snapshot.staleLocks())
                .withDetail("staleLockAgeThresholdSeconds", snapshot.staleLockAgeThresholdSeconds())
                .withDetail("maxLockAgeSeconds", snapshot.maxLockAgeSeconds())
                .withDetail("maxRemainingLockSeconds", snapshot.maxRemainingLockSeconds())
                .withDetail("sampleSize", snapshot.activeLockSamples().size())
                .build();
    }
}
