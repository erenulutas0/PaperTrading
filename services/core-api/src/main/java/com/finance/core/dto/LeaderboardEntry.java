package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class LeaderboardEntry {
    private int rank;
    private UUID portfolioId;
    private String portfolioName;
    private String ownerId;
    private String ownerName;
    private BigDecimal returnPercentage;
    private BigDecimal totalEquity;
    private BigDecimal profitLoss;
    private BigDecimal startEquity;
}
