package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class StrategyBotResponse {
    UUID id;
    UUID userId;
    UUID linkedPortfolioId;
    String name;
    String description;
    String botKind;
    String status;
    String market;
    String symbol;
    String timeframe;
    JsonNode entryRules;
    JsonNode exitRules;
    BigDecimal maxPositionSizePercent;
    BigDecimal stopLossPercent;
    BigDecimal takeProfitPercent;
    Integer cooldownMinutes;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
