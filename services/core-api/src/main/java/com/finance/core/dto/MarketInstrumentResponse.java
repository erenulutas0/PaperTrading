package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketInstrumentResponse {
    private String symbol;
    private String displayName;
    private String assetType;
    private double currentPrice;
    private double changePercent24h;
}
