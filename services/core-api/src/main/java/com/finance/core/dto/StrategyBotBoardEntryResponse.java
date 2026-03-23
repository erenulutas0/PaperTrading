package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class StrategyBotBoardEntryResponse {
    UUID strategyBotId;
    String description;
    String name;
    String status;
    String market;
    String symbol;
    String timeframe;
    UUID linkedPortfolioId;
    String linkedPortfolioName;
    UUID ownerId;
    String ownerUsername;
    String ownerDisplayName;
    String ownerAvatarUrl;
    Double ownerTrustScore;
    int totalRuns;
    int completedRuns;
    int runningRuns;
    int failedRuns;
    int cancelledRuns;
    int totalSimulatedTrades;
    int positiveCompletedRuns;
    int negativeCompletedRuns;
    Double avgReturnPercent;
    Double avgNetPnl;
    Double avgMaxDrawdownPercent;
    Double avgWinRate;
    Double avgProfitFactor;
    Double avgExpectancyPerTrade;
    LocalDateTime latestRequestedAt;
    StrategyBotRunScorecardResponse bestRun;
    StrategyBotRunScorecardResponse latestCompletedRun;
    StrategyBotRunScorecardResponse activeForwardRun;
}
