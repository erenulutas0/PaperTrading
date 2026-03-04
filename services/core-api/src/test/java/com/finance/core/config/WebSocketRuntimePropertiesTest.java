package com.finance.core.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketRuntimePropertiesTest {

    @Test
    void normalizedBrokerMode_shouldFallbackToSimple() {
        WebSocketRuntimeProperties properties = new WebSocketRuntimeProperties();
        properties.setBrokerMode("something-else");

        assertEquals("SIMPLE", properties.normalizedBrokerMode());
        assertFalse(properties.isRelayBrokerMode());
    }

    @Test
    void normalizedBrokerMode_shouldSupportRelay() {
        WebSocketRuntimeProperties properties = new WebSocketRuntimeProperties();
        properties.setBrokerMode("relay");

        assertEquals("RELAY", properties.normalizedBrokerMode());
        assertTrue(properties.isRelayBrokerMode());
    }
}

