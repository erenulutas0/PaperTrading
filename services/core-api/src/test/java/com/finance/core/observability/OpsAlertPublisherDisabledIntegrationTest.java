package com.finance.core.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(properties = {
        "app.alerting.enabled=false",
        "app.auth.observability.enabled=true"
})
class OpsAlertPublisherDisabledIntegrationTest {

    @Autowired
    private OpsAlertPublisher opsAlertPublisher;

    @Test
    void shouldProvideNoOpPublisherWhenAlertingDisabled() {
        assertInstanceOf(NoOpOpsAlertPublisher.class, opsAlertPublisher);
    }
}
