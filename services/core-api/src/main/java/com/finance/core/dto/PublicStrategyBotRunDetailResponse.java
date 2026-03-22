package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PublicStrategyBotRunDetailResponse {
    UUID strategyBotId;
    UUID runId;
    String botName;
    String botDescription;
    String botStatus;
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
    String runMode;
    String status;
    BigDecimal requestedInitialCapital;
    BigDecimal effectiveInitialCapital;
    LocalDate fromDate;
    LocalDate toDate;
    JsonNode compiledEntryRules;
    JsonNode compiledExitRules;
    JsonNode summary;
    String errorMessage;
    LocalDateTime requestedAt;
    LocalDateTime startedAt;
    LocalDateTime completedAt;
    List<StrategyBotRunFillResponse> fills;
    List<StrategyBotRunEquityPointResponse> equityCurve;
}
