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
class AuthSessionEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeAuthSessionsActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/authsessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowSeconds").exists())
                .andExpect(jsonPath("$.minSamples").exists())
                .andExpect(jsonPath("$.refreshSuccessCount").exists())
                .andExpect(jsonPath("$.invalidRefreshCount").exists())
                .andExpect(jsonPath("$.alertState").exists());
    }
}
