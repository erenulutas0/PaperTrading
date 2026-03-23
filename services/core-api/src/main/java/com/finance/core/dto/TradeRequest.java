package com.finance.core.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TradeRequest {
    private String portfolioId; // UUID as String from frontend
    private String symbol;
    private BigDecimal quantity;
    private Integer leverage = 1; // Default to 1x (Spot)
    private String side; // LONG or SHORT
}
