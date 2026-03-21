package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class StrategyBotRequest {
    private String name;
    private String description;
    private UUID linkedPortfolioId;
    private String market;
    private String symbol;
    private String timeframe;
    private JsonNode entryRules;
    private JsonNode exitRules;
    private BigDecimal maxPositionSizePercent;
    private BigDecimal stopLossPercent;
    private BigDecimal takeProfitPercent;
    private Integer cooldownMinutes;
    private String status;
}
