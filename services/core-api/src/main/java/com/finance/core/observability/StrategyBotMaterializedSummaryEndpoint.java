package com.finance.core.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "strategybotsummaries")
public class StrategyBotMaterializedSummaryEndpoint {

    private final StrategyBotMaterializedSummaryObservabilityService service;

    public StrategyBotMaterializedSummaryEndpoint(StrategyBotMaterializedSummaryObservabilityService service) {
        this.service = service;
    }

    @ReadOperation
    public StrategyBotMaterializedSummarySchedulerSnapshot status() {
        return service.snapshot();
    }
}
