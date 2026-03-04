package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.feed.observability")
@Getter
@Setter
public class FeedObservabilityProperties {

    private boolean enabled = true;
    private Duration refreshInterval = Duration.ofSeconds(30);
    private List<String> uris = List.of(
            "/api/v1/feed/global",
            "/api/v1/feed",
            "/api/v1/feed/user/{userId}"
    );
    private long minSamples = 100;
    private double warningP95Ms = 50.0;
    private double warningP99Ms = 170.0;
    private double criticalP99Ms = 220.0;
}
