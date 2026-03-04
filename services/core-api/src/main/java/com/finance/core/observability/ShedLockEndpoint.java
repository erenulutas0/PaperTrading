package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "shedlock")
@ConditionalOnProperty(name = "app.shedlock.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ShedLockEndpoint {

    private final ShedLockObservabilityService shedLockObservabilityService;

    public ShedLockEndpoint(ShedLockObservabilityService shedLockObservabilityService) {
        this.shedLockObservabilityService = shedLockObservabilityService;
    }

    @ReadOperation
    public ShedLockSnapshot shedLockStatus() {
        return shedLockObservabilityService.getLatestSnapshot();
    }
}
