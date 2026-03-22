package com.finance.core.observability;

import com.finance.core.domain.StrategyBotRun;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StrategyBotForwardTestEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StrategyBotForwardTestObservabilityService observabilityService;

    @Test
    void shouldExposeStrategyBotForwardTestSchedulerSnapshot() throws Exception {
        UUID runId = UUID.randomUUID();
        observabilityService.recordSchedulerTick(2);
        observabilityService.recordRefreshSuccess(runId, StrategyBotRun.Status.RUNNING);

        mockMvc.perform(get("/actuator/strategybotforwardtests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshIntervalSeconds").isNumber())
                .andExpect(jsonPath("$.scheduledTickCount").isNumber())
                .andExpect(jsonPath("$.lastObservedRunningRunCount").value(2))
                .andExpect(jsonPath("$.refreshAttemptCount").isNumber())
                .andExpect(jsonPath("$.refreshSuccessCount").isNumber())
                .andExpect(jsonPath("$.lastTickAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRefreshAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRefreshedRunId").value(runId.toString()))
                .andExpect(jsonPath("$.lastRefreshedRunStatus").value("RUNNING"));
    }
}
