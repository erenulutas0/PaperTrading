package com.finance.core.observability;

import com.finance.core.domain.StrategyBotRun;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyBotForwardTestObservabilityServiceTest {

    @Test
    void snapshot_shouldExposeSkipTelemetryAlongsideRefreshOutcomeCounters() {
        StrategyBotForwardTestObservabilityService service =
                new StrategyBotForwardTestObservabilityService(Duration.ofSeconds(45));
        UUID skippedRunId = UUID.randomUUID();
        UUID refreshedRunId = UUID.randomUUID();

        service.recordSchedulerTick(3);
        service.recordRefreshSkip(skippedRunId, "run_no_longer_refreshable");
        service.recordRefreshFailure(UUID.randomUUID(), "market data unavailable");
        service.recordRefreshSuccess(refreshedRunId, StrategyBotRun.Status.COMPLETED);

        StrategyBotForwardTestSchedulerSnapshot snapshot = service.snapshot();

        assertThat(snapshot.refreshIntervalSeconds()).isEqualTo(45);
        assertThat(snapshot.scheduledTickCount()).isEqualTo(1);
        assertThat(snapshot.lastObservedRunningRunCount()).isEqualTo(3);
        assertThat(snapshot.refreshAttemptCount()).isEqualTo(3);
        assertThat(snapshot.refreshSuccessCount()).isEqualTo(1);
        assertThat(snapshot.refreshFailureCount()).isEqualTo(1);
        assertThat(snapshot.refreshSkipCount()).isEqualTo(1);
        assertThat(snapshot.lastTickAt()).isNotNull();
        assertThat(snapshot.lastRefreshAt()).isNotNull();
        assertThat(snapshot.lastSkipAt()).isNotNull();
        assertThat(snapshot.lastRefreshedRunId()).isEqualTo(refreshedRunId);
        assertThat(snapshot.lastRefreshedRunStatus()).isEqualTo("COMPLETED");
        assertThat(snapshot.lastSkippedRunId()).isEqualTo(skippedRunId);
        assertThat(snapshot.lastSkipReason()).isEqualTo("run_no_longer_refreshable");
        assertThat(snapshot.lastError()).isNull();
    }
}
