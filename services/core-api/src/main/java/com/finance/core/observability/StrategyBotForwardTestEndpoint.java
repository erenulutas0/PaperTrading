package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "strategybotforwardtests")
public class StrategyBotForwardTestEndpoint {

    private final StrategyBotForwardTestObservabilityService service;

    public StrategyBotForwardTestEndpoint(StrategyBotForwardTestObservabilityService service) {
        this.service = service;
    }

    @ReadOperation
    public StrategyBotForwardTestSchedulerSnapshot status() {
        return service.snapshot();
    }
}
