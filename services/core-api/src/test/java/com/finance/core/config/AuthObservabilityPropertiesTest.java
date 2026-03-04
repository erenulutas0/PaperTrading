package com.finance.core.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthObservabilityPropertiesTest {

    @Test
    void normalizedValues_shouldClampAndFallbackInvalidInputs() {
        AuthObservabilityProperties properties = new AuthObservabilityProperties();
        properties.setRefreshInterval(Duration.ZERO);
        properties.setChurnWindow(Duration.ofSeconds(-1));
        properties.setMinSamples(0);
        properties.setWarningRefreshCount(1000);
        properties.setCriticalRefreshCount(100);
        properties.setWarningInvalidCount(99);
        properties.setCriticalInvalidCount(9);
        properties.setWarningInvalidRatio(3.0);
        properties.setCriticalInvalidRatio(-1.0);

        assertEquals(Duration.ofSeconds(30), properties.normalizedRefreshInterval());
        assertEquals(Duration.ofMinutes(10), properties.normalizedChurnWindow());
        assertEquals(1, properties.normalizedMinSamples());
        assertEquals(100, properties.normalizedCriticalRefreshCount());
        assertEquals(100, properties.normalizedWarningRefreshCount());
        assertEquals(9, properties.normalizedCriticalInvalidCount());
        assertEquals(9, properties.normalizedWarningInvalidCount());
        assertEquals(0.0, properties.normalizedCriticalInvalidRatio());
        assertEquals(0.0, properties.normalizedWarningInvalidRatio());
    }
}
