package com.finance.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class MarketChartNoteResponse {
    UUID id;
    MarketType market;
    String symbol;
    String body;
    boolean pinned;
    LocalDateTime createdAt;
}
