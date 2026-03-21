package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class StrategyBotRunFillResponse {
    UUID id;
    UUID strategyBotRunId;
    Integer sequenceNo;
    String side;
    Long openTime;
    BigDecimal price;
    BigDecimal quantity;
    BigDecimal realizedPnl;
    JsonNode matchedRules;
}
