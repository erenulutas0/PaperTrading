package com.finance.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNotificationPreferencesRequest {

    private InAppPreferences inApp;
    private String digestCadence;
    private QuietHoursPreferences quietHours;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InAppPreferences {
        private Boolean social;
        private Boolean watchlist;
        private Boolean tournaments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuietHoursPreferences {
        private Boolean enabled;
        private String start;
        private String end;
    }
}
