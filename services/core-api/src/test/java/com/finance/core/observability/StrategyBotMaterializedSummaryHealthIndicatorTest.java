package com.finance.core.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyBotMaterializedSummaryHealthIndicatorTest {

    @Test
    void healthShouldBeUnknownBeforeFirstRefreshInsideGraceWindow() {
        StrategyBotMaterializedSummaryObservabilityService observabilityService =
                new StrategyBotMaterializedSummaryObservabilityService(Duration.ofMinutes(15), Duration.ofDays(7), 25);
        StrategyBotMaterializedSummaryHealthIndicator indicator =
                new StrategyBotMaterializedSummaryHealthIndicator(observabilityService, Duration.ofMinutes(45));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void healthShouldBeUpAfterSuccessfulRefresh() {
        StrategyBotMaterializedSummaryObservabilityService observabilityService =
                new StrategyBotMaterializedSummaryObservabilityService(Duration.ofMinutes(15), Duration.ofDays(7), 25);
        observabilityService.recordRefreshSuccess(3);
        StrategyBotMaterializedSummaryHealthIndicator indicator =
                new StrategyBotMaterializedSummaryHealthIndicator(observabilityService, Duration.ofMinutes(45));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("lastRefreshedBotCount", 3);
    }
}
