package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "idempotency")
@ConditionalOnProperty(name = "app.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyEndpoint {

    private final IdempotencyObservabilityService service;

    public IdempotencyEndpoint(IdempotencyObservabilityService service) {
        this.service = service;
    }

    @ReadOperation
    public IdempotencySnapshot idempotencyStatus() {
        return service.refreshSnapshot();
    }
}
