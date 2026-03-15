package com.finance.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTerminalPreferencesRequest {
    private String market;
    private String symbol;
    private List<String> compareSymbols;
    private Boolean compareVisible;
    private String range;
    private String interval;
    private List<String> favoriteSymbols;
    private List<CompareBasket> compareBaskets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareBasket {
        private String name;
        private String market;
        private List<String> symbols;
        private String updatedAt;
    }
}
