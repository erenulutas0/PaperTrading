package com.finance.core.observability;

import java.time.LocalDateTime;
import java.util.List;

public record ShedLockSnapshot(
        LocalDateTime checkedAt,
        int activeLocks,
        int staleLocks,
        double maxLockAgeSeconds,
        double maxRemainingLockSeconds,
        long staleLockAgeThresholdSeconds,
        List<ShedLockEntry> activeLockSamples,
        String error
) {
    public static ShedLockSnapshot empty() {
        return new ShedLockSnapshot(
                null,
                0,
                0,
                0.0,
                0.0,
                0L,
                List.of(),
                null
        );
    }

    public boolean available() {
        return error == null;
    }
}
