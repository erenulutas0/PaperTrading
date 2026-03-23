package com.finance.core.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.show-details=always",
        "management.endpoint.health.show-components=always"
})
@AutoConfigureMockMvc
class StrategyBotForwardTestHealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StrategyBotForwardTestObservabilityService observabilityService;

    @Test
    void shouldExposeStrategyBotForwardTestHealthComponent() throws Exception {
        observabilityService.recordSchedulerTick(2);

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.strategyBotForwardTests.status").value("UP"))
                .andExpect(jsonPath("$.components.strategyBotForwardTests.details.staleThresholdSeconds").isNumber())
                .andExpect(jsonPath("$.components.strategyBotForwardTests.details.scheduledTickCount").isNumber())
                .andExpect(jsonPath("$.components.strategyBotForwardTests.details.lastObservedRunningRunCount").value(2))
                .andExpect(jsonPath("$.components.strategyBotForwardTests.details.lastRefreshAt").value(""));

        mockMvc.perform(get("/actuator/health/strategyBotForwardTests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details.staleThresholdSeconds").isNumber())
                .andExpect(jsonPath("$.details.scheduledTickCount").isNumber())
                .andExpect(jsonPath("$.details.lastObservedRunningRunCount").value(2));
    }
}
