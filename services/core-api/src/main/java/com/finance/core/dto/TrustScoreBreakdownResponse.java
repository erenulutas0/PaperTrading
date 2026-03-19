package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TrustScoreBreakdownResponse {
    private double blendedWinRate;
    private double predictionWinRate;
    private double predictionPosteriorRate;
    private long resolvedPredictionCount;
    private double tradeWinRate;
    private double tradePosteriorRate;
    private long resolvedTradeCount;
    private int profitablePortfolioCount;
    private int totalPortfolioCount;
    private double portfolioWinRate;
    private double portfolioPosteriorRate;
    private BigDecimal averagePortfolioReturn;
    private BigDecimal aggregateRealizedPnl;
    private long totalEvidenceCount;
    private double confidenceScore;
    private double predictionComponent;
    private double tradeComponent;
    private double portfolioComponent;
    private double returnComponent;
    private double experienceComponent;
}
