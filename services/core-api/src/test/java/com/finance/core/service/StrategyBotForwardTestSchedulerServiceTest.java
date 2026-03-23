package com.finance.core.service;

import com.finance.core.domain.StrategyBotRun;
import com.finance.core.observability.StrategyBotForwardTestObservabilityService;
import com.finance.core.repository.StrategyBotRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyBotForwardTestSchedulerServiceTest {

    @Mock
    private StrategyBotRunRepository strategyBotRunRepository;
    @Mock
    private StrategyBotRunService strategyBotRunService;
    @Mock
    private StrategyBotForwardTestObservabilityService observabilityService;

    @InjectMocks
    private StrategyBotForwardTestSchedulerService schedulerService;

    @Test
    void refreshRunningForwardTests_shouldRecordSchedulerTickAndRunSuccess() {
        UUID runId = UUID.randomUUID();
        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();
        StrategyBotRun refreshed = StrategyBotRun.builder()
                .id(runId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();

        when(strategyBotRunRepository.findByRunModeAndStatusOrderByRequestedAtAsc(
                StrategyBotRun.RunMode.FORWARD_TEST,
                StrategyBotRun.Status.RUNNING)).thenReturn(List.of(run));
        when(strategyBotRunService.refreshForwardTestRunSystem(runId)).thenReturn(refreshed);

        schedulerService.refreshRunningForwardTests();

        verify(observabilityService).recordSchedulerTick(1);
        verify(observabilityService).recordRefreshSuccess(runId, StrategyBotRun.Status.RUNNING);
        verify(observabilityService, never()).recordRefreshFailure(eq(runId), anyString());
    }

    @Test
    void refreshRunningForwardTests_shouldRecordFailureStatusAndContinue() {
        UUID failedRunId = UUID.randomUUID();
        UUID completedRunId = UUID.randomUUID();
        StrategyBotRun failedRun = StrategyBotRun.builder()
                .id(failedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();
        StrategyBotRun completedRun = StrategyBotRun.builder()
                .id(completedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();
        StrategyBotRun failedResult = StrategyBotRun.builder()
                .id(failedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.FAILED)
                .errorMessage("market data unavailable")
                .build();
        StrategyBotRun completedResult = StrategyBotRun.builder()
                .id(completedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.COMPLETED)
                .build();

        when(strategyBotRunRepository.findByRunModeAndStatusOrderByRequestedAtAsc(
                StrategyBotRun.RunMode.FORWARD_TEST,
                StrategyBotRun.Status.RUNNING)).thenReturn(List.of(failedRun, completedRun));
        when(strategyBotRunService.refreshForwardTestRunSystem(failedRunId)).thenReturn(failedResult);
        when(strategyBotRunService.refreshForwardTestRunSystem(completedRunId)).thenReturn(completedResult);

        schedulerService.refreshRunningForwardTests();

        verify(observabilityService).recordSchedulerTick(2);
        verify(observabilityService).recordRefreshFailure(failedRunId, "market data unavailable");
        verify(observabilityService).recordRefreshSuccess(completedRunId, StrategyBotRun.Status.COMPLETED);
    }

    @Test
    void refreshRunningForwardTests_shouldRecordSkipWhenRunIsNoLongerRefreshable() {
        UUID runId = UUID.randomUUID();
        StrategyBotRun run = StrategyBotRun.builder()
                .id(runId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();

        when(strategyBotRunRepository.findByRunModeAndStatusOrderByRequestedAtAsc(
                StrategyBotRun.RunMode.FORWARD_TEST,
                StrategyBotRun.Status.RUNNING)).thenReturn(List.of(run));
        when(strategyBotRunService.refreshForwardTestRunSystem(runId)).thenReturn(null);

        schedulerService.refreshRunningForwardTests();

        verify(observabilityService).recordSchedulerTick(1);
        verify(observabilityService).recordRefreshSkip(runId, "run_no_longer_refreshable");
        verify(observabilityService, never()).recordRefreshSuccess(eq(runId), eq(StrategyBotRun.Status.RUNNING));
        verify(observabilityService, never()).recordRefreshFailure(eq(runId), anyString());
    }

    @Test
    void refreshRunningForwardTests_shouldRecordThrownRefreshFailureAndContinue() {
        UUID failedRunId = UUID.randomUUID();
        UUID completedRunId = UUID.randomUUID();
        StrategyBotRun failedRun = StrategyBotRun.builder()
                .id(failedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();
        StrategyBotRun completedRun = StrategyBotRun.builder()
                .id(completedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();
        StrategyBotRun completedResult = StrategyBotRun.builder()
                .id(completedRunId)
                .runMode(StrategyBotRun.RunMode.FORWARD_TEST)
                .status(StrategyBotRun.Status.RUNNING)
                .build();

        when(strategyBotRunRepository.findByRunModeAndStatusOrderByRequestedAtAsc(
                StrategyBotRun.RunMode.FORWARD_TEST,
                StrategyBotRun.Status.RUNNING)).thenReturn(List.of(failedRun, completedRun));
        when(strategyBotRunService.refreshForwardTestRunSystem(failedRunId))
                .thenThrow(new IllegalStateException("state drift"));
        when(strategyBotRunService.refreshForwardTestRunSystem(completedRunId)).thenReturn(completedResult);

        schedulerService.refreshRunningForwardTests();

        verify(observabilityService).recordSchedulerTick(2);
        verify(observabilityService).recordRefreshFailure(failedRunId, "state drift");
        verify(observabilityService).recordRefreshSuccess(completedRunId, StrategyBotRun.Status.RUNNING);
    }
}
