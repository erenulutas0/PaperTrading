package com.finance.core.observability;

import com.finance.core.config.IdempotencyProperties;
import com.finance.core.domain.IdempotencyKeyRecord;
import com.finance.core.repository.IdempotencyKeyRepository;
import com.finance.core.service.IdempotencyService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.idempotency.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyObservabilityService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final IdempotencyService idempotencyService;
    private final IdempotencyProperties properties;

    private final AtomicReference<IdempotencySnapshot> latestSnapshot = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastCleanupAt = new AtomicReference<>();
    private final AtomicLong lastCleanupDeletedCount = new AtomicLong(0);
    private final AtomicLong claimedCount = new AtomicLong(0);
    private final AtomicLong replayCount = new AtomicLong(0);
    private final AtomicLong conflictCount = new AtomicLong(0);
    private final AtomicLong inProgressConflictCount = new AtomicLong(0);
    private final AtomicLong completedResponseCount = new AtomicLong(0);
    private final AtomicLong releasedCount = new AtomicLong(0);
    private final AtomicLong skippedLargeResponseCount = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastClaimedAt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastReplayAt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastConflictAt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastInProgressConflictAt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastCompletedResponseAt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastReleasedAt = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastSkippedLargeResponseAt = new AtomicReference<>();

    @PostConstruct
    void initialize() {
        latestSnapshot.set(emptySnapshot());
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval:PT30M}")
    @SchedulerLock(name = "IdempotencyObservabilityService.cleanupExpiredScheduled", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5S")
    public void cleanupExpiredScheduled() {
        cleanupExpiredNow();
    }

    public IdempotencySnapshot cleanupExpiredNow() {
        LocalDateTime cutoff = LocalDateTime.now();
        long before = idempotencyKeyRepository.countByExpiresAtBefore(cutoff);
        idempotencyService.purgeExpired(cutoff);
        lastCleanupAt.set(LocalDateTime.now());
        lastCleanupDeletedCount.set(before);
        IdempotencySnapshot snapshot = refreshSnapshot();
        if (before > 0) {
            log.info("Purged {} expired idempotency records", before);
        }
        return snapshot;
    }

    public IdempotencySnapshot getLatestSnapshot() {
        IdempotencySnapshot snapshot = latestSnapshot.get();
        if (snapshot == null || snapshot.checkedAt() == null) {
            return refreshSnapshot();
        }
        return snapshot;
    }

    public void recordClaimed() {
        record(claimedCount, lastClaimedAt);
    }

    public void recordReplay() {
        record(replayCount, lastReplayAt);
    }

    public void recordConflict() {
        record(conflictCount, lastConflictAt);
    }

    public void recordInProgressConflict() {
        record(inProgressConflictCount, lastInProgressConflictAt);
    }

    public void recordCompletedResponse() {
        record(completedResponseCount, lastCompletedResponseAt);
    }

    public void recordReleased() {
        record(releasedCount, lastReleasedAt);
    }

    public void recordSkippedLargeResponse() {
        record(skippedLargeResponseCount, lastSkippedLargeResponseAt);
    }

    void resetRuntimeTelemetry() {
        claimedCount.set(0);
        replayCount.set(0);
        conflictCount.set(0);
        inProgressConflictCount.set(0);
        completedResponseCount.set(0);
        releasedCount.set(0);
        skippedLargeResponseCount.set(0);
        lastClaimedAt.set(null);
        lastReplayAt.set(null);
        lastConflictAt.set(null);
        lastInProgressConflictAt.set(null);
        lastCompletedResponseAt.set(null);
        lastReleasedAt.set(null);
        lastSkippedLargeResponseAt.set(null);
        lastCleanupAt.set(null);
        lastCleanupDeletedCount.set(0);
        latestSnapshot.set(emptySnapshot());
    }

    public IdempotencySnapshot refreshSnapshot() {
        try {
            LocalDateTime now = LocalDateTime.now();
            long total = idempotencyKeyRepository.count();
            long inProgress = idempotencyKeyRepository.countByStatus(IdempotencyKeyRecord.Status.IN_PROGRESS);
            long completed = idempotencyKeyRepository.countByStatus(IdempotencyKeyRecord.Status.COMPLETED);
            long expired = idempotencyKeyRepository.countByExpiresAtBefore(now);
            Double oldestExpiredAgeSeconds = idempotencyKeyRepository
                    .findFirstByExpiresAtBeforeOrderByExpiresAtAsc(now)
                    .map(record -> (double) ChronoUnit.SECONDS.between(record.getExpiresAt(), now))
                    .orElse(null);

            IdempotencySnapshot snapshot = buildSnapshot(
                    now,
                    total,
                    inProgress,
                    completed,
                    expired,
                    oldestExpiredAgeSeconds,
                    null);
            latestSnapshot.set(snapshot);
            return snapshot;
        } catch (Exception ex) {
            log.error("Failed to collect idempotency snapshot", ex);
            IdempotencySnapshot errorSnapshot = buildSnapshot(
                    LocalDateTime.now(),
                    0,
                    0,
                    0,
                    0,
                    null,
                    ex.getMessage());
            latestSnapshot.set(errorSnapshot);
            return errorSnapshot;
        }
    }

    private IdempotencySnapshot emptySnapshot() {
        return IdempotencySnapshot.empty(
                properties.isEnabled(),
                safeToSeconds(properties.getTtl()),
                safeToSeconds(properties.getCleanupInterval()));
    }

    private IdempotencySnapshot buildSnapshot(
            LocalDateTime checkedAt,
            long totalRecords,
            long inProgressRecords,
            long completedRecords,
            long expiredRecords,
            Double oldestExpiredAgeSeconds,
            String error) {
        return new IdempotencySnapshot(
                checkedAt,
                properties.isEnabled(),
                safeToSeconds(properties.getTtl()),
                safeToSeconds(properties.getCleanupInterval()),
                totalRecords,
                inProgressRecords,
                completedRecords,
                expiredRecords,
                oldestExpiredAgeSeconds,
                claimedCount.get(),
                replayCount.get(),
                conflictCount.get(),
                inProgressConflictCount.get(),
                completedResponseCount.get(),
                releasedCount.get(),
                skippedLargeResponseCount.get(),
                lastClaimedAt.get(),
                lastReplayAt.get(),
                lastConflictAt.get(),
                lastInProgressConflictAt.get(),
                lastCompletedResponseAt.get(),
                lastReleasedAt.get(),
                lastSkippedLargeResponseAt.get(),
                lastCleanupAt.get(),
                lastCleanupDeletedCount.get(),
                error
        );
    }

    private void record(AtomicLong counter, AtomicReference<LocalDateTime> lastAt) {
        counter.incrementAndGet();
        lastAt.set(LocalDateTime.now());
    }

    private long safeToSeconds(Duration duration) {
        return duration == null ? 0 : duration.toSeconds();
    }
}
