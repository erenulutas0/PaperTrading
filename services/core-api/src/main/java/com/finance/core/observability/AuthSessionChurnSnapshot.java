package com.finance.core.observability;

import java.time.LocalDateTime;

public record AuthSessionChurnSnapshot(
        LocalDateTime checkedAt,
        long windowSeconds,
        long minSamples,
        long warningRefreshCount,
        long criticalRefreshCount,
        long warningInvalidCount,
        long criticalInvalidCount,
        double warningInvalidRatio,
        double criticalInvalidRatio,
        long refreshSuccessCount,
        long invalidRefreshCount,
        long totalRefreshAttempts,
        double invalidRefreshRatio,
        boolean warningBreach,
        boolean criticalBreach,
        String alertState,
        String error
) {
    public boolean available() {
        return error == null;
    }

    public boolean hasCriticalBreach() {
        return criticalBreach;
    }

    public AuthSessionChurnSnapshot withAlertState(String nextAlertState) {
        return new AuthSessionChurnSnapshot(
                checkedAt,
                windowSeconds,
                minSamples,
                warningRefreshCount,
                criticalRefreshCount,
                warningInvalidCount,
                criticalInvalidCount,
                warningInvalidRatio,
                criticalInvalidRatio,
                refreshSuccessCount,
                invalidRefreshCount,
                totalRefreshAttempts,
                invalidRefreshRatio,
                warningBreach,
                criticalBreach,
                nextAlertState,
                error
        );
    }

    public static AuthSessionChurnSnapshot empty() {
        return new AuthSessionChurnSnapshot(
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0,
                0,
                0,
                0.0,
                false,
                false,
                "NONE",
                null
        );
    }
}
