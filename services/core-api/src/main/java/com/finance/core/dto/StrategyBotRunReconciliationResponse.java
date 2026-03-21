package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class StrategyBotRunReconciliationResponse {
    UUID runId;
    UUID strategyBotId;
    UUID linkedPortfolioId;
    String linkedPortfolioName;
    String symbol;
    String runStatus;
    boolean targetPositionOpen;
    BigDecimal targetQuantity;
    BigDecimal targetAveragePrice;
    BigDecimal targetLastPrice;
    BigDecimal targetCashBalance;
    BigDecimal targetEquity;
    BigDecimal currentCashBalance;
    BigDecimal currentQuantity;
    BigDecimal currentAveragePrice;
    BigDecimal quantityDelta;
    BigDecimal cashDelta;
    boolean cashAligned;
    boolean quantityAligned;
    boolean portfolioAligned;
    int extraSymbolCount;
    List<String> warnings;
}
