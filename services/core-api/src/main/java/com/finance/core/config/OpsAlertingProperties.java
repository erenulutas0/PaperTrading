package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.alerting")
@Getter
@Setter
public class OpsAlertingProperties {

    private boolean enabled = true;
    private Duration cooldown = Duration.ofMinutes(5);
    private String webhookUrl = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
}
