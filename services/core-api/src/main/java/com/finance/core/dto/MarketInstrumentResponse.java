package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketInstrumentResponse {
    private String symbol;
    private String displayName;
    private String assetType;
    private String market;
    private String exchange;
    private String currency;
    private String sector;
    private String delayLabel;
    private double currentPrice;
    private double changePercent24h;
}
