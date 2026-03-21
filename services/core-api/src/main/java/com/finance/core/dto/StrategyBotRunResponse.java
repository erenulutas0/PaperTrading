package com.finance.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class StrategyBotRunResponse {
    UUID id;
    UUID strategyBotId;
    UUID userId;
    UUID linkedPortfolioId;
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
}
