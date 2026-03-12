package com.finance.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MarketCandleResponse {
    private long openTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}
