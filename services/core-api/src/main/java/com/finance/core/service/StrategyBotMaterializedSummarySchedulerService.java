package com.finance.core.service;

import com.finance.core.observability.StrategyBotMaterializedSummaryObservabilityService;
import com.finance.core.repository.StrategyBotRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyBotMaterializedSummarySchedulerService {

    private final StrategyBotRunRepository strategyBotRunRepository;
    private final StrategyBotRunService strategyBotRunService;
    private final StrategyBotMaterializedSummaryObservabilityService observabilityService;

    @Value("${app.strategy-bots.summary-refresh-activity-window:PT168H}")
    private Duration activityWindow;

    @Value("${app.strategy-bots.summary-refresh-batch-size:250}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.strategy-bots.summary-refresh-interval:PT15M}")
    @SchedulerLock(name = "StrategyBotSummaryRefresh.refresh", lockAtMostFor = "PT20M", lockAtLeastFor = "PT5S")
    public void refreshRecentBotSummaries() {
        try {
            LocalDateTime activityCutoff = LocalDateTime.now().minus(activityWindow);
            int effectiveBatchSize = Math.max(batchSize, 1);
            List<UUID> botIds = strategyBotRunRepository.findRecentlyActiveStrategyBotIds(activityCutoff, effectiveBatchSize);
            if (botIds.isEmpty()) {
                observabilityService.recordRefreshSuccess(0);
                log.debug("No recently active strategy bots found for materialized summary refresh");
                return;
            }

            strategyBotRunService.refreshMaterializedSummariesForBots(botIds);
            observabilityService.recordRefreshSuccess(botIds.size());
            log.info("Refreshed materialized strategy-bot summaries for {} recently active bots", botIds.size());
        } catch (Exception ex) {
            observabilityService.recordRefreshFailure(ex.getMessage());
            throw ex;
        }
    }
}
