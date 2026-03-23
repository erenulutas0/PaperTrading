package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "strategybotforwardtests")
public class StrategyBotForwardTestEndpoint {

    private final StrategyBotForwardTestAlertingService service;

    public StrategyBotForwardTestEndpoint(StrategyBotForwardTestAlertingService service) {
        this.service = service;
    }

    @ReadOperation
    public StrategyBotForwardTestStatusSnapshot status() {
        return service.statusSnapshot();
    }
}
