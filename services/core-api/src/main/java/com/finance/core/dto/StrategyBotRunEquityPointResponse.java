package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

@Value
@Builder
public class StrategyBotRunEquityPointResponse {
    UUID id;
    UUID strategyBotRunId;
    Integer sequenceNo;
    Long openTime;
    BigDecimal closePrice;
    BigDecimal equity;
}
