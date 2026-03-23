package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class StrategyBotRunEventResponse {
    UUID id;
    UUID strategyBotRunId;
    Integer sequenceNo;
    Long openTime;
    String phase;
    String action;
    BigDecimal closePrice;
    BigDecimal cashBalance;
    BigDecimal positionQuantity;
    BigDecimal equity;
    JsonNode matchedRules;
    JsonNode details;
}
