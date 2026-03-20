package com.finance.core.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.websocket.canary.enabled=true",
        "app.websocket.canary.ws-url=ws://localhost:8080/ws"
})
@AutoConfigureMockMvc
class WebSocketCanaryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebSocketCanaryClient webSocketCanaryClient;

    @BeforeEach
    void setUp() {
        when(webSocketCanaryClient.probe(any()))
                .thenReturn(new WebSocketCanaryProbeResult(true, true, 12, null));
    }

    @Test
    void shouldExposeWebSocketCanaryActuatorEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/websocketcanary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.latencyMs").exists())
                .andExpect(jsonPath("$.topicDestination").exists())
                .andExpect(jsonPath("$.userQueueDestination").exists());
    }

    @Test
    void shouldReturnLatestSnapshotWithoutTriggeringProbeWhenRefreshDisabled() throws Exception {
        mockMvc.perform(get("/actuator/websocketcanary").param("refresh", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.consecutiveFailures").value(0))
                .andExpect(jsonPath("$.checkedAt").doesNotExist());

        verify(webSocketCanaryClient, never()).probe(any());
    }
}
