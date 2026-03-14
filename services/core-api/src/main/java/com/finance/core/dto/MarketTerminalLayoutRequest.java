package com.finance.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketTerminalLayoutRequest {
    private String name;
    private UUID watchlistId;
    private String market;
    private String symbol;
    private List<String> compareSymbols;
    private Boolean compareVisible;
    private String range;
    private String interval;
    private List<String> favoriteSymbols;
}
