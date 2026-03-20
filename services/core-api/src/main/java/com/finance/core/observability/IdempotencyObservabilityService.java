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

    @PostConstruct
    void initialize() {
        latestSnapshot.set(IdempotencySnapshot.empty(
                properties.isEnabled(),
                properties.getTtl().toSeconds(),
                properties.getCleanupInterval().toSeconds()));
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

            IdempotencySnapshot snapshot = new IdempotencySnapshot(
                    now,
                    properties.isEnabled(),
                    properties.getTtl().toSeconds(),
                    properties.getCleanupInterval().toSeconds(),
                    total,
                    inProgress,
                    completed,
                    expired,
                    oldestExpiredAgeSeconds,
                    lastCleanupAt.get(),
                    lastCleanupDeletedCount.get(),
                    null
            );
            latestSnapshot.set(snapshot);
            return snapshot;
        } catch (Exception ex) {
            log.error("Failed to collect idempotency snapshot", ex);
            IdempotencySnapshot errorSnapshot = new IdempotencySnapshot(
                    LocalDateTime.now(),
                    properties.isEnabled(),
                    safeToSeconds(properties.getTtl()),
                    safeToSeconds(properties.getCleanupInterval()),
                    0,
                    0,
                    0,
                    0,
                    null,
                    lastCleanupAt.get(),
                    lastCleanupDeletedCount.get(),
                    ex.getMessage()
            );
            latestSnapshot.set(errorSnapshot);
            return errorSnapshot;
        }
    }

    private long safeToSeconds(Duration duration) {
        return duration == null ? 0 : duration.toSeconds();
    }
}
