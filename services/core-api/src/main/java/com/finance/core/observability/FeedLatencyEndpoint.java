package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "feedlatency")
@ConditionalOnProperty(name = "app.feed.observability.enabled", havingValue = "true", matchIfMissing = true)
public class FeedLatencyEndpoint {

    private final FeedLatencyObservabilityService service;

    public FeedLatencyEndpoint(FeedLatencyObservabilityService service) {
        this.service = service;
    }

    @ReadOperation
    public FeedLatencySnapshot feedLatencyStatus() {
        return service.refreshSnapshot();
    }
}
