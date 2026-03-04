package com.finance.core.observability;

import com.finance.core.config.OpsAlertingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.alerting.enabled", havingValue = "true", matchIfMissing = true)
public class RestOpsWebhookClient implements OpsWebhookClient {

    private final RestClient restClient;

    public RestOpsWebhookClient(OpsAlertingProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Math.max(1, properties.getConnectTimeout().toMillis()));
        requestFactory.setReadTimeout((int) Math.max(1, properties.getReadTimeout().toMillis()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void post(String webhookUrl, Map<String, Object> payload) {
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
