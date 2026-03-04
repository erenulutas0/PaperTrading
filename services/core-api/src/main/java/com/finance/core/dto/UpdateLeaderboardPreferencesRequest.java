package com.finance.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLeaderboardPreferencesRequest {

    private DashboardPreferences dashboard;
    private PublicPreferences publicPage;

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
}

