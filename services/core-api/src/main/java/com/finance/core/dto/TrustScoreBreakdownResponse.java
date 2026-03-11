package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TrustScoreBreakdownResponse {
    private double predictionWinRate;
    private long resolvedPredictionCount;
    private double tradeWinRate;
    private long resolvedTradeCount;
    private int profitablePortfolioCount;
    private int totalPortfolioCount;
    private double portfolioWinRate;
    private BigDecimal averagePortfolioReturn;
    private BigDecimal aggregateRealizedPnl;
    private double predictionComponent;
    private double tradeComponent;
    private double portfolioComponent;
    private double returnComponent;
    private double experienceComponent;
}
