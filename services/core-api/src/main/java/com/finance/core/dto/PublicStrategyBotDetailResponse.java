package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
@Jacksonized
public class PublicStrategyBotDetailResponse {
    UUID strategyBotId;
    String name;
    String description;
    String botKind;
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
    BigDecimal maxPositionSizePercent;
    BigDecimal stopLossPercent;
    BigDecimal takeProfitPercent;
    Integer cooldownMinutes;
    JsonNode entryRules;
    JsonNode exitRules;
    StrategyBotAnalyticsResponse analytics;
}
