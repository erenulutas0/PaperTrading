package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "websocket")
@ConditionalOnProperty(name = "app.websocket.observability.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketEndpoint {

    private final WebSocketObservabilityService webSocketObservabilityService;

    public WebSocketEndpoint(WebSocketObservabilityService webSocketObservabilityService) {
        this.webSocketObservabilityService = webSocketObservabilityService;
    }

    @ReadOperation
    public WebSocketObservabilitySnapshot websocketStatus() {
        return webSocketObservabilityService.getSnapshot();
    }
}
