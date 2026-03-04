package com.finance.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioResponse {

    private UUID id;
    private String name;
    private String ownerId;
    private String description;
    private String visibility;
    private BigDecimal balance;
    private BigDecimal totalEquity;
    private BigDecimal returnPercentage1D;
    private BigDecimal returnPercentage1W;
    private BigDecimal returnPercentage1M;
    private BigDecimal returnPercentage1Y;
    private BigDecimal returnPercentageALL;
    private Double maxDrawdown;
    private Double sharpeRatio;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PortfolioItemDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PortfolioItemDto {
        private UUID id;
        private String symbol;
        private BigDecimal quantity;
        private BigDecimal averagePrice;
        private Integer leverage;
        private String side;
    }
}
