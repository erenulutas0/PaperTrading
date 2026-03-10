package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.idempotency")
@Getter
@Setter
public class IdempotencyProperties {

    private boolean enabled = true;
    private Duration ttl = Duration.ofHours(24);
    private int maxBodyBytes = 65536;
    private Duration cleanupInterval = Duration.ofMinutes(30);
}
