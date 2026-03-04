package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.shedlock.observability")
@Getter
@Setter
public class ShedLockObservabilityProperties {

    private boolean enabled = true;
    private Duration refreshInterval = Duration.ofSeconds(30);
    private Duration staleLockAgeThreshold = Duration.ofMinutes(15);
    private int maxReportLocks = 20;
    private int alertStaleLockCount = 1;
}
