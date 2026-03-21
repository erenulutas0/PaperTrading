package com.finance.core.service;

import com.finance.core.domain.StrategyBotRun;
import com.finance.core.repository.StrategyBotRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StrategyBotForwardTestSchedulerService {

    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotRunService strategyBotRunService;

    @Scheduled(fixedDelayString = "${app.strategy-bots.forward-test-refresh-interval:PT30S}")
    public void refreshRunningForwardTests() {
        strategyBotRunRepository
                .findByRunModeAndStatusOrderByRequestedAtAsc(StrategyBotRun.RunMode.FORWARD_TEST, StrategyBotRun.Status.RUNNING)
                .forEach(run -> strategyBotRunService.refreshForwardTestRunSystem(run.getId()));
    }
}
