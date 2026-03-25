package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DiscoverPortfolioHighlightResponse {
    private UUID id;
    private String name;
    private String description;
    private String ownerId;
    private String ownerName;
    private BigDecimal returnPercentage1W;
    private BigDecimal profitLoss1W;
    private BigDecimal totalEquity;
    private LocalDateTime createdAt;
}
