package com.finance.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesResponse {

    private LeaderboardPreferences leaderboard;
    private TerminalPreferences terminal;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LeaderboardPreferences {
        private DashboardPreferences dashboard;
        private PublicPreferences publicPage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardPreferences {
        private String period;
        private String sortBy;
        private String direction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicPreferences {
        private String sortBy;
        private String direction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerminalPreferences {
        private String market;
        private String symbol;
        private java.util.List<String> compareSymbols;
        private Boolean compareVisible;
        private String range;
        private String interval;
        private java.util.List<String> favoriteSymbols;
    }
}
