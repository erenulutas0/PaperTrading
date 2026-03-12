package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AccountLeaderboardEntry {
    private int rank;
    private UUID ownerId;
    private String ownerName;
    private BigDecimal returnPercentage;
    private BigDecimal totalEquity;
    private BigDecimal profitLoss;
    private BigDecimal startEquity;
    private int publicPortfolioCount;
    private double trustScore;
    private double winRate;
}
