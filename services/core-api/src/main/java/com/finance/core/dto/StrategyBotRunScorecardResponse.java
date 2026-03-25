package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
@Jacksonized
public class StrategyBotRunScorecardResponse {
    UUID id;
    String runMode;
    String status;
    LocalDateTime requestedAt;
    LocalDateTime completedAt;
    Double returnPercent;
    Double netPnl;
    Double maxDrawdownPercent;
    Double winRate;
    Integer tradeCount;
    Double profitFactor;
    Double expectancyPerTrade;
    Double timeInMarketPercent;
    Boolean linkedPortfolioAligned;
    Boolean executionEngineReady;
    Long lastEvaluatedOpenTime;
    String errorMessage;
}
