package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.websocket.observability")
@Getter
@Setter
public class WebSocketObservabilityProperties {

    private boolean enabled = true;
    private Duration reconnectWindow = Duration.ofMinutes(2);

    public Duration normalizedReconnectWindow() {
        if (reconnectWindow == null || reconnectWindow.isZero() || reconnectWindow.isNegative()) {
            return Duration.ofMinutes(2);
        }
        return reconnectWindow;
    }
}
