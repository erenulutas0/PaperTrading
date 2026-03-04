package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth.observability")
@Getter
@Setter
public class AuthObservabilityProperties {

    private boolean enabled = true;
    private Duration refreshInterval = Duration.ofSeconds(30);
    private Duration churnWindow = Duration.ofMinutes(10);
    private long minSamples = 20;
    private long warningRefreshCount = 300;
    private long criticalRefreshCount = 800;
    private long warningInvalidCount = 20;
    private long criticalInvalidCount = 50;
    private double warningInvalidRatio = 0.10;
    private double criticalInvalidRatio = 0.25;
    private boolean alertOnRecovery = true;

    public Duration normalizedRefreshInterval() {
        if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
            return Duration.ofSeconds(30);
        }
        return refreshInterval;
    }

    public Duration normalizedChurnWindow() {
        if (churnWindow == null || churnWindow.isZero() || churnWindow.isNegative()) {
            return Duration.ofMinutes(10);
        }
        return churnWindow;
    }

    public long normalizedMinSamples() {
        return Math.max(1, minSamples);
    }

    public long normalizedCriticalRefreshCount() {
        return Math.max(1, criticalRefreshCount);
    }

    public long normalizedWarningRefreshCount() {
        return Math.max(1, Math.min(warningRefreshCount, normalizedCriticalRefreshCount()));
    }

    public long normalizedCriticalInvalidCount() {
        return Math.max(1, criticalInvalidCount);
    }

    public long normalizedWarningInvalidCount() {
        return Math.max(1, Math.min(warningInvalidCount, normalizedCriticalInvalidCount()));
    }

    public double normalizedCriticalInvalidRatio() {
        return clampRatio(criticalInvalidRatio);
    }

    public double normalizedWarningInvalidRatio() {
        return clampRatio(Math.min(warningInvalidRatio, normalizedCriticalInvalidRatio()));
    }

    private double clampRatio(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(ratio, 1.0));
    }
}
