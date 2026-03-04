package com.finance.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "app.websocket")
@Getter
@Setter
public class WebSocketRuntimeProperties {

    private String brokerMode = "SIMPLE";

    private String relayHost = "localhost";
    private int relayPort = 61613;
    private String relayClientLogin = "guest";
    private String relayClientPasscode = "guest";
    private String relaySystemLogin = "guest";
    private String relaySystemPasscode = "guest";
    private String relayVirtualHost = "/";

    private List<String> allowedOriginPatterns = List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3005",
            "http://localhost:3010");

    private long serverHeartbeatMs = 10000;
    private long clientHeartbeatMs = 10000;
    private int sendTimeLimitMs = 15000;
    private int sendBufferSizeLimitBytes = 512 * 1024;
    private int messageSizeLimitBytes = 64 * 1024;

    /**
     * Legacy compatibility channel:
     * /topic/notifications/{userId}
     * Keep disabled by default to avoid user-id addressable topic exposure.
     */
    private boolean legacyUserTopicBroadcastEnabled = false;

    public boolean isRelayBrokerMode() {
        return "RELAY".equalsIgnoreCase(brokerMode);
    }

    public String normalizedBrokerMode() {
        if (brokerMode == null) {
            return "SIMPLE";
        }
        String normalized = brokerMode.trim().toUpperCase(Locale.ROOT);
        return "RELAY".equals(normalized) ? "RELAY" : "SIMPLE";
    }
}
