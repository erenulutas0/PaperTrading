package com.finance.core.observability;

import java.time.LocalDateTime;

public record IdempotencySnapshot(
        LocalDateTime checkedAt,
        boolean enabled,
        long ttlSeconds,
        long cleanupIntervalSeconds,
        long totalRecords,
        long inProgressRecords,
        long completedRecords,
        long expiredRecords,
        Double oldestExpiredAgeSeconds,
        long claimedCount,
        long replayCount,
        long conflictCount,
        long inProgressConflictCount,
        long completedResponseCount,
        long releasedCount,
        long skippedLargeResponseCount,
        LocalDateTime lastClaimedAt,
        LocalDateTime lastReplayAt,
        LocalDateTime lastConflictAt,
        LocalDateTime lastInProgressConflictAt,
        LocalDateTime lastCompletedResponseAt,
        LocalDateTime lastReleasedAt,
        LocalDateTime lastSkippedLargeResponseAt,
        LocalDateTime lastCleanupAt,
        long lastCleanupDeletedCount,
        String error
) {
    public static IdempotencySnapshot empty(boolean enabled, long ttlSeconds, long cleanupIntervalSeconds) {
        return new IdempotencySnapshot(
                null,
                enabled,
                ttlSeconds,
                cleanupIntervalSeconds,
                0,
                0,
                0,
                0,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null
        );
    }
}
