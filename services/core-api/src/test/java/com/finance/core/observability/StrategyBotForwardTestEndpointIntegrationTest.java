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
        UUID skippedRunId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        observabilityService.recordSchedulerTick(2);
        observabilityService.recordRefreshSkip(skippedRunId, "run_no_longer_refreshable");
        observabilityService.recordRefreshSuccess(runId, StrategyBotRun.Status.RUNNING);

        mockMvc.perform(get("/actuator/strategybotforwardtests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshIntervalSeconds").isNumber())
                .andExpect(jsonPath("$.staleThresholdSeconds").isNumber())
                .andExpect(jsonPath("$.alertState").value("NONE"))
                .andExpect(jsonPath("$.lastTickAgeSeconds").isNumber())
                .andExpect(jsonPath("$.scheduledTickCount").isNumber())
                .andExpect(jsonPath("$.lastObservedRunningRunCount").value(2))
                .andExpect(jsonPath("$.refreshAttemptCount").isNumber())
                .andExpect(jsonPath("$.refreshSuccessCount").isNumber())
                .andExpect(jsonPath("$.refreshSkipCount").isNumber())
                .andExpect(jsonPath("$.lastTickAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRefreshAt").isNotEmpty())
                .andExpect(jsonPath("$.lastSkipAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRefreshedRunId").value(runId.toString()))
                .andExpect(jsonPath("$.lastRefreshedRunStatus").value("RUNNING"))
                .andExpect(jsonPath("$.lastSkippedRunId").value(skippedRunId.toString()))
                .andExpect(jsonPath("$.lastSkipReason").value("run_no_longer_refreshable"));
    }
}
