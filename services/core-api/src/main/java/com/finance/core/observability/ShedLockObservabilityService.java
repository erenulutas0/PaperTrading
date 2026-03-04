package com.finance.core.observability;

import com.finance.core.config.ShedLockObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.shedlock.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ShedLockObservabilityService {

    private static final String COMPONENT = "shedlock";
    private static final String WARNING_ALERT_KEY = "stale-lock-threshold";

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    private final ShedLockObservabilityProperties properties;
    private final OpsAlertPublisher opsAlertPublisher;

    private final AtomicInteger activeLocksGauge = new AtomicInteger(0);
    private final AtomicInteger staleLocksGauge = new AtomicInteger(0);
    private final AtomicReference<Double> maxLockAgeGauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> maxRemainingGauge = new AtomicReference<>(0.0);

    private final AtomicReference<ShedLockSnapshot> latestSnapshot = new AtomicReference<>(ShedLockSnapshot.empty());

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge("shedlock.active.locks", activeLocksGauge);
        meterRegistry.gauge("shedlock.stale.locks", staleLocksGauge);
        meterRegistry.gauge("shedlock.max.lock.age.seconds", maxLockAgeGauge, AtomicReference::get);
        meterRegistry.gauge("shedlock.max.lock.remaining.seconds", maxRemainingGauge, AtomicReference::get);
    }

    @Scheduled(fixedDelayString = "${app.shedlock.observability.refresh-interval:PT30S}")
    public void refreshSnapshotScheduled() {
        refreshSnapshot();
    }

    public ShedLockSnapshot getLatestSnapshot() {
        ShedLockSnapshot snapshot = latestSnapshot.get();
        if (snapshot.checkedAt() == null) {
            return refreshSnapshot();
        }

        LocalDateTime nextRefreshAt = snapshot.checkedAt().plus(properties.getRefreshInterval());
        if (LocalDateTime.now().isAfter(nextRefreshAt)) {
            return refreshSnapshot();
        }

        return snapshot;
    }

    public ShedLockSnapshot refreshSnapshot() {
        try {
            ShedLockSnapshot snapshot = collectSnapshot();
            latestSnapshot.set(snapshot);
            updateGauges(snapshot);

            if (snapshot.staleLocks() >= properties.getAlertStaleLockCount()) {
                opsAlertPublisher.publish(
                        COMPONENT,
                        OpsAlertSeverity.WARNING,
                        WARNING_ALERT_KEY,
                        "ShedLock stale lock threshold breached",
                        Map.of(
                                "staleLocks", snapshot.staleLocks(),
                                "activeLocks", snapshot.activeLocks(),
                                "maxLockAgeSeconds", snapshot.maxLockAgeSeconds(),
                                "maxRemainingLockSeconds", snapshot.maxRemainingLockSeconds(),
                                "staleLockAgeThresholdSeconds", snapshot.staleLockAgeThresholdSeconds(),
                                "alertStaleLockCount", properties.getAlertStaleLockCount()
                        )
                );
            }

            return snapshot;
        } catch (Exception ex) {
            log.error("Failed to collect ShedLock observability snapshot", ex);
            ShedLockSnapshot errorSnapshot = new ShedLockSnapshot(
                    LocalDateTime.now(),
                    0,
                    0,
                    0.0,
                    0.0,
                    properties.getStaleLockAgeThreshold().toSeconds(),
                    List.of(),
                    ex.getMessage()
            );
            latestSnapshot.set(errorSnapshot);
            updateGauges(errorSnapshot);
            return errorSnapshot;
        }
    }

    private ShedLockSnapshot collectSnapshot() {
        long staleThresholdSeconds = properties.getStaleLockAgeThreshold().toSeconds();
        int maxReportLocks = Math.max(1, properties.getMaxReportLocks());

        int activeLocks = queryInt("""
                SELECT COUNT(*)
                FROM shedlock
                WHERE lock_until > CURRENT_TIMESTAMP
                """);

        int staleLocks = queryInt("""
                SELECT COUNT(*)
                FROM shedlock
                WHERE lock_until > CURRENT_TIMESTAMP
                  AND EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - locked_at)) > ?
                """, staleThresholdSeconds);

        double maxLockAgeSeconds = queryDouble("""
                SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - locked_at))), 0)
                FROM shedlock
                WHERE lock_until > CURRENT_TIMESTAMP
                """);

        double maxRemainingLockSeconds = queryDouble("""
                SELECT COALESCE(MAX(EXTRACT(EPOCH FROM (lock_until - CURRENT_TIMESTAMP))), 0)
                FROM shedlock
                WHERE lock_until > CURRENT_TIMESTAMP
                """);

        List<ShedLockEntry> samples = jdbcTemplate.query("""
                        SELECT name,
                               locked_by,
                               locked_at,
                               lock_until,
                               EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - locked_at)) AS age_seconds,
                               EXTRACT(EPOCH FROM (lock_until - CURRENT_TIMESTAMP)) AS remaining_seconds
                        FROM shedlock
                        WHERE lock_until > CURRENT_TIMESTAMP
                        ORDER BY age_seconds DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> {
                    double ageSeconds = rs.getDouble("age_seconds");
                    return new ShedLockEntry(
                            rs.getString("name"),
                            rs.getString("locked_by"),
                            toLocalDateTime(rs.getTimestamp("locked_at")),
                            toLocalDateTime(rs.getTimestamp("lock_until")),
                            ageSeconds,
                            rs.getDouble("remaining_seconds"),
                            ageSeconds > staleThresholdSeconds
                    );
                },
                maxReportLocks
        );

        return new ShedLockSnapshot(
                LocalDateTime.now(),
                activeLocks,
                staleLocks,
                maxLockAgeSeconds,
                maxRemainingLockSeconds,
                staleThresholdSeconds,
                samples,
                null
        );
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value != null ? value : 0;
    }

    private double queryDouble(String sql, Object... args) {
        Double value = jdbcTemplate.queryForObject(sql, Double.class, args);
        return value != null ? value : 0.0;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    private void updateGauges(ShedLockSnapshot snapshot) {
        activeLocksGauge.set(snapshot.activeLocks());
        staleLocksGauge.set(snapshot.staleLocks());
        maxLockAgeGauge.set(snapshot.maxLockAgeSeconds());
        maxRemainingGauge.set(snapshot.maxRemainingLockSeconds());
    }
}
