package com.finance.core.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.alerting.enabled=true",
        "app.alerting.webhook-url=http://localhost:9999/ops"
})
@AutoConfigureMockMvc
class OpsAlertEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpsWebhookClient opsWebhookClient;

    @BeforeEach
    void setUp() {
        reset(opsWebhookClient);
    }

    @Test
    void shouldExposeOpsAlertActuatorStatus() throws Exception {
        mockMvc.perform(get("/actuator/opsalerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.webhookConfigured").value(true))
                .andExpect(jsonPath("$.cooldownSeconds").isNumber());
    }

    @Test
    void shouldPublishManualOpsAlertAndExposeMetrics() throws Exception {
        String component = "ops-alert-endpoint-it";

        mockMvc.perform(post("/actuator/opsalerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "component": "%s",
                                  "severity": "critical",
                                  "alertKey": "endpoint-it-key",
                                  "message": "endpoint integration test"
                                }
                                """.formatted(component)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.component").value(component))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.alertKey").value("endpoint-it-key"))
                .andExpect(jsonPath("$.webhookConfigured").value(true));

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(opsWebhookClient, times(1)).post(eq("http://localhost:9999/ops"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload.get("component")).isEqualTo(component);
        assertThat(payload.get("severity")).isEqualTo("CRITICAL");
        assertThat(payload.get("alertKey")).isEqualTo("endpoint-it-key");

    }

    @Test
    void shouldRejectInvalidSeverityWithoutPublishing() throws Exception {
        mockMvc.perform(post("/actuator/opsalerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "severity": "broken"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.error").value("invalid_severity"));
    }
}
