package com.finance.core.observability;

import java.time.LocalDateTime;

public record ShedLockEntry(
        String name,
        String lockedBy,
        LocalDateTime lockedAt,
        LocalDateTime lockUntil,
        double ageSeconds,
        double remainingSeconds,
        boolean stale
) {
}
