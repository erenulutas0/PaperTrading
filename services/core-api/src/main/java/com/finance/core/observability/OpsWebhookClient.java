package com.finance.core.observability;

import java.util.Map;

public interface OpsWebhookClient {

    void post(String webhookUrl, Map<String, Object> payload);
}
