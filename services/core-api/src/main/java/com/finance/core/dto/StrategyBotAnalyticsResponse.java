package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class StrategyBotAnalyticsResponse {
    UUID strategyBotId;
    int totalRuns;
    int backtestRuns;
    int forwardTestRuns;
    int completedRuns;
    int runningRuns;
    int failedRuns;
    int cancelledRuns;
    int compilerReadyRuns;
    int positiveCompletedRuns;
    int negativeCompletedRuns;
    int totalSimulatedTrades;
    Double avgReturnPercent;
    Double avgNetPnl;
    Double avgMaxDrawdownPercent;
    Double avgWinRate;
    Double avgTradeCount;
    Double avgProfitFactor;
    Double avgExpectancyPerTrade;
    StrategyBotRunScorecardResponse bestRun;
    StrategyBotRunScorecardResponse worstRun;
    StrategyBotRunScorecardResponse latestCompletedRun;
    StrategyBotRunScorecardResponse activeForwardRun;
    Map<String, Integer> entryDriverTotals;
    Map<String, Integer> exitDriverTotals;
    List<StrategyBotRunScorecardResponse> recentScorecards;
}
