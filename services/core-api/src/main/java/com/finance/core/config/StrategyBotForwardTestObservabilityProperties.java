package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.strategy-bots.forward-test-observability")
@Getter
@Setter
public class StrategyBotForwardTestObservabilityProperties {

    private Duration refreshInterval = Duration.ofSeconds(30);
    private Duration staleThreshold = Duration.ofMinutes(2);
    private boolean alertOnRecovery = true;

    public Duration normalizedRefreshInterval() {
        if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
            return Duration.ofSeconds(30);
        }
        return refreshInterval;
    }

    public Duration normalizedStaleThreshold() {
        if (staleThreshold == null || staleThreshold.isZero() || staleThreshold.isNegative()) {
            return Duration.ofMinutes(2);
        }
        return staleThreshold;
    }
}
