package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "authsessions")
@ConditionalOnProperty(name = "app.auth.observability.enabled", havingValue = "true", matchIfMissing = true)
public class AuthSessionEndpoint {

    private final AuthSessionObservabilityService service;

    public AuthSessionEndpoint(AuthSessionObservabilityService service) {
        this.service = service;
    }

    @ReadOperation
    public AuthSessionChurnSnapshot authSessionStatus() {
        return service.refreshSnapshot();
    }
}
