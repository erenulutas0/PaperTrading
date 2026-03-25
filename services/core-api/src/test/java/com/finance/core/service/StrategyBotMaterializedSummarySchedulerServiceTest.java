package com.finance.core.service;

import com.finance.core.repository.StrategyBotRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyBotMaterializedSummarySchedulerServiceTest {

    @Mock
    private StrategyBotRunRepository strategyBotRunRepository;
    @Mock
    private StrategyBotRunService strategyBotRunService;
    @Mock
    private com.finance.core.observability.StrategyBotMaterializedSummaryObservabilityService observabilityService;

    @InjectMocks
    private StrategyBotMaterializedSummarySchedulerService schedulerService;

    @Test
    void refreshRecentBotSummaries_shouldRefreshRecentlyActiveBotIds() {
        UUID firstBotId = UUID.randomUUID();
        UUID secondBotId = UUID.randomUUID();
        ReflectionTestUtils.setField(schedulerService, "activityWindow", Duration.ofDays(7));
        ReflectionTestUtils.setField(schedulerService, "batchSize", 25);
        when(strategyBotRunRepository.findRecentlyActiveStrategyBotIds(any(LocalDateTime.class), eq(25)))
                .thenReturn(List.of(firstBotId, secondBotId));

        schedulerService.refreshRecentBotSummaries();

        verify(strategyBotRunRepository).findRecentlyActiveStrategyBotIds(any(LocalDateTime.class), eq(25));
        verify(strategyBotRunService).refreshMaterializedSummariesForBots(List.of(firstBotId, secondBotId));
        verify(observabilityService).recordRefreshSuccess(2);
    }

    @Test
    void refreshRecentBotSummaries_shouldClampInvalidBatchSizeAndSkipEmptyRefresh() {
        ReflectionTestUtils.setField(schedulerService, "activityWindow", Duration.ofDays(3));
        ReflectionTestUtils.setField(schedulerService, "batchSize", 0);
        when(strategyBotRunRepository.findRecentlyActiveStrategyBotIds(any(LocalDateTime.class), eq(1)))
                .thenReturn(List.of());

        schedulerService.refreshRecentBotSummaries();

        verify(strategyBotRunRepository).findRecentlyActiveStrategyBotIds(any(LocalDateTime.class), eq(1));
        verify(strategyBotRunService, never()).refreshMaterializedSummariesForBots(any());
        verify(observabilityService).recordRefreshSuccess(0);
    }

    @Test
    void refreshRecentBotSummaries_shouldRecordFailureAndRethrow() {
        ReflectionTestUtils.setField(schedulerService, "activityWindow", Duration.ofDays(2));
        ReflectionTestUtils.setField(schedulerService, "batchSize", 10);
        when(strategyBotRunRepository.findRecentlyActiveStrategyBotIds(any(LocalDateTime.class), eq(10)))
                .thenThrow(new IllegalStateException("projection drift"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, schedulerService::refreshRecentBotSummaries);

        verify(observabilityService).recordRefreshFailure("projection drift");
    }
}
