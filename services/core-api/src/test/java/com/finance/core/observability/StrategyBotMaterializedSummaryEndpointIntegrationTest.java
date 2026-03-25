package com.finance.core.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StrategyBotMaterializedSummaryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StrategyBotMaterializedSummaryObservabilityService observabilityService;

    @Test
    void shouldExposeStrategyBotSummarySchedulerSnapshot() throws Exception {
        observabilityService.recordRefreshSuccess(4);

        mockMvc.perform(get("/actuator/strategybotsummaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.refreshIntervalSeconds").isNumber())
                .andExpect(jsonPath("$.activityWindowSeconds").isNumber())
                .andExpect(jsonPath("$.batchSize").isNumber())
                .andExpect(jsonPath("$.scheduledTickCount").isNumber())
                .andExpect(jsonPath("$.refreshSuccessCount").isNumber())
                .andExpect(jsonPath("$.refreshFailureCount").isNumber())
                .andExpect(jsonPath("$.lastRefreshAt").isNotEmpty())
                .andExpect(jsonPath("$.lastSuccessAt").isNotEmpty())
                .andExpect(jsonPath("$.lastRefreshedBotCount").value(4))
                .andExpect(jsonPath("$.lastError").isEmpty());
    }
}
