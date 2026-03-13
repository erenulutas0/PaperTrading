package com.finance.core.dto;

import lombok.Data;

@Data
public class MarketChartNoteRequest {
    private MarketType market;
    private String symbol;
    private String body;
    private Boolean pinned;
}
