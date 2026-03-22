package com.finance.core.service;

import com.finance.core.domain.StrategyBotRun;
import com.finance.core.observability.StrategyBotForwardTestObservabilityService;
import com.finance.core.repository.StrategyBotRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyBotForwardTestSchedulerService {

    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotRunService strategyBotRunService;
    private final StrategyBotForwardTestObservabilityService observabilityService;

    @Scheduled(fixedDelayString = "${app.strategy-bots.forward-test-refresh-interval:PT30S}")
    @SchedulerLock(name = "StrategyBotForwardTestSchedulerService.refreshRunningForwardTests", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void refreshRunningForwardTests() {
        List<StrategyBotRun> runs = strategyBotRunRepository
                .findByRunModeAndStatusOrderByRequestedAtAsc(StrategyBotRun.RunMode.FORWARD_TEST, StrategyBotRun.Status.RUNNING);
        observabilityService.recordSchedulerTick(runs.size());
        for (StrategyBotRun run : runs) {
            try {
                StrategyBotRun refreshedRun = strategyBotRunService.refreshForwardTestRunSystem(run.getId());
                if (refreshedRun == null) {
                    continue;
                }
                if (refreshedRun.getStatus() == StrategyBotRun.Status.FAILED) {
                    observabilityService.recordRefreshFailure(run.getId(), refreshedRun.getErrorMessage());
                } else {
                    observabilityService.recordRefreshSuccess(run.getId(), refreshedRun.getStatus());
                }
            } catch (Exception ex) {
                log.warn("Strategy bot forward-test scheduler refresh failed for run {}: {}", run.getId(), ex.getMessage());
                observabilityService.recordRefreshFailure(run.getId(), ex.getMessage());
            }
        }
    }
}
