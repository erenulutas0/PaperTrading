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
class WebSocketEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldExposeWebSocketActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/websocket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeSessions").exists())
                .andExpect(jsonPath("$.connectEvents").exists())
                .andExpect(jsonPath("$.disconnectEvents").exists())
                .andExpect(jsonPath("$.stompErrorEvents").exists())
                .andExpect(jsonPath("$.reconnectSuccessRatio").exists())
                .andExpect(jsonPath("$.stompErrorsByCommand").exists());
    }
}
