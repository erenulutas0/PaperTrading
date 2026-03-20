package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "websocketcanary")
@ConditionalOnProperty(name = "app.websocket.canary.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketCanaryEndpoint {

    private final WebSocketCanaryService webSocketCanaryService;

    public WebSocketCanaryEndpoint(WebSocketCanaryService webSocketCanaryService) {
        this.webSocketCanaryService = webSocketCanaryService;
    }

    @ReadOperation
    public WebSocketCanarySnapshot websocketCanaryStatus(@Nullable Boolean refresh) {
        if (Boolean.FALSE.equals(refresh)) {
            return webSocketCanaryService.getLatestSnapshot();
        }
        return webSocketCanaryService.runCanaryProbe();
    }
}
