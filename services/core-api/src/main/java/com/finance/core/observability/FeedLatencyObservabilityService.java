package com.finance.core.observability;

import com.finance.core.config.FeedObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.feed.observability.enabled", havingValue = "true", matchIfMissing = true)
public class FeedLatencyObservabilityService {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";
    private static final double P50 = 0.50;
    private static final double P95 = 0.95;
    private static final double P99 = 0.99;
    private static final double EPSILON = 0.0001;
    private static final String COMPONENT = "feed-latency";
    private static final String WARNING_ALERT_KEY = "warning-breach";
    private static final String CRITICAL_ALERT_KEY = "critical-breach";

    private final MeterRegistry meterRegistry;
    private final FeedObservabilityProperties properties;
    private final OpsAlertPublisher opsAlertPublisher;

    private final AtomicReference<FeedLatencySnapshot> latestSnapshot = new AtomicReference<>(FeedLatencySnapshot.empty());
    private final AtomicReference<Double> maxP95Gauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> maxP99Gauge = new AtomicReference<>(0.0);
    private final AtomicInteger warningBreachesGauge = new AtomicInteger(0);
    private final AtomicInteger criticalBreachesGauge = new AtomicInteger(0);

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge("feed.latency.max.p95.ms", maxP95Gauge, AtomicReference::get);
        meterRegistry.gauge("feed.latency.max.p99.ms", maxP99Gauge, AtomicReference::get);
        meterRegistry.gauge("feed.latency.warning.breaches", warningBreachesGauge);
        meterRegistry.gauge("feed.latency.critical.breaches", criticalBreachesGauge);
    }

    @Scheduled(fixedDelayString = "${app.feed.observability.refresh-interval:PT30S}")
    public void refreshSnapshotScheduled() {
        refreshSnapshot();
    }

    public FeedLatencySnapshot getLatestSnapshot() {
        FeedLatencySnapshot snapshot = latestSnapshot.get();
        if (snapshot.checkedAt() == null) {
            return refreshSnapshot();
        }

        LocalDateTime nextRefreshAt = snapshot.checkedAt().plus(properties.getRefreshInterval());
        if (LocalDateTime.now().isAfter(nextRefreshAt)) {
            return refreshSnapshot();
        }

        return snapshot;
    }

    public FeedLatencySnapshot refreshSnapshot() {
        try {
            FeedLatencySnapshot snapshot = collectSnapshot();
            latestSnapshot.set(snapshot);
            updateGauges(snapshot);

            if (snapshot.criticalBreaches() > 0) {
                opsAlertPublisher.publish(
                        COMPONENT,
                        OpsAlertSeverity.CRITICAL,
                        CRITICAL_ALERT_KEY,
                        "Feed latency critical threshold breached",
                        Map.of(
                                "criticalBreaches", snapshot.criticalBreaches(),
                                "warningBreaches", snapshot.warningBreaches(),
                                "warningP95Ms", snapshot.warningP95Ms(),
                                "warningP99Ms", snapshot.warningP99Ms(),
                                "criticalP99Ms", snapshot.criticalP99Ms(),
                                "maxObservedP95Ms", maxP95(snapshot),
                                "maxObservedP99Ms", maxP99(snapshot)
                        )
                );
            } else if (snapshot.warningBreaches() > 0) {
                opsAlertPublisher.publish(
                        COMPONENT,
                        OpsAlertSeverity.WARNING,
                        WARNING_ALERT_KEY,
                        "Feed latency warning threshold breached",
                        Map.of(
                                "warningBreaches", snapshot.warningBreaches(),
                                "warningP95Ms", snapshot.warningP95Ms(),
                                "warningP99Ms", snapshot.warningP99Ms(),
                                "maxObservedP95Ms", maxP95(snapshot),
                                "maxObservedP99Ms", maxP99(snapshot)
                        )
                );
            }

            return snapshot;
        } catch (Exception ex) {
            log.error("Failed to collect feed latency snapshot", ex);
            FeedLatencySnapshot errorSnapshot = new FeedLatencySnapshot(
                    LocalDateTime.now(),
                    properties.getMinSamples(),
                    properties.getWarningP95Ms(),
                    properties.getWarningP99Ms(),
                    properties.getCriticalP99Ms(),
                    0,
                    0,
                    List.of(),
                    ex.getMessage()
            );
            latestSnapshot.set(errorSnapshot);
            updateGauges(errorSnapshot);
            return errorSnapshot;
        }
    }

    private FeedLatencySnapshot collectSnapshot() {
        List<FeedLatencyPoint> points = new ArrayList<>();
        int warningBreaches = 0;
        int criticalBreaches = 0;

        for (String uri : properties.getUris()) {
            FeedLatencyPoint point = collectUriPoint(uri);
            points.add(point);
            if (point.warningBreach()) {
                warningBreaches++;
            }
            if (point.criticalBreach()) {
                criticalBreaches++;
            }
        }

        return new FeedLatencySnapshot(
                LocalDateTime.now(),
                properties.getMinSamples(),
                properties.getWarningP95Ms(),
                properties.getWarningP99Ms(),
                properties.getCriticalP99Ms(),
                warningBreaches,
                criticalBreaches,
                points,
                null
        );
    }

    private FeedLatencyPoint collectUriPoint(String uri) {
        Timer timer = meterRegistry.find(HTTP_SERVER_REQUESTS)
                .tags("uri", uri)
                .timer();

        if (timer == null || timer.count() == 0) {
            return new FeedLatencyPoint(uri, 0, 0.0, 0.0, 0.0, 0.0, false, false, false);
        }

        HistogramSnapshot histogram = timer.takeSnapshot();
        long count = timer.count();
        double p50Ms = percentileMs(histogram, P50);
        double p95Ms = percentileMs(histogram, P95);
        double p99Ms = percentileMs(histogram, P99);
        double maxMs = nanosToMillis(histogram.max());
        boolean sufficientSamples = count >= properties.getMinSamples();
        boolean warningBreach = sufficientSamples
                && (p95Ms >= properties.getWarningP95Ms() || p99Ms >= properties.getWarningP99Ms());
        boolean criticalBreach = sufficientSamples
                && p99Ms >= properties.getCriticalP99Ms();

        return new FeedLatencyPoint(
                uri,
                count,
                p50Ms,
                p95Ms,
                p99Ms,
                maxMs,
                sufficientSamples,
                warningBreach,
                criticalBreach
        );
    }

    private double percentileMs(HistogramSnapshot snapshot, double percentile) {
        for (ValueAtPercentile value : snapshot.percentileValues()) {
            if (Math.abs(value.percentile() - percentile) < EPSILON) {
                return nanosToMillis(value.value());
            }
        }
        return 0.0;
    }

    private double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }

    private void updateGauges(FeedLatencySnapshot snapshot) {
        double maxP95 = 0.0;
        double maxP99 = 0.0;
        for (FeedLatencyPoint point : snapshot.points()) {
            maxP95 = Math.max(maxP95, point.p95Ms());
            maxP99 = Math.max(maxP99, point.p99Ms());
        }
        maxP95Gauge.set(maxP95);
        maxP99Gauge.set(maxP99);
        warningBreachesGauge.set(snapshot.warningBreaches());
        criticalBreachesGauge.set(snapshot.criticalBreaches());
    }

    private double maxP95(FeedLatencySnapshot snapshot) {
        double max = 0.0;
        for (FeedLatencyPoint point : snapshot.points()) {
            max = Math.max(max, point.p95Ms());
        }
        return max;
    }

    private double maxP99(FeedLatencySnapshot snapshot) {
        double max = 0.0;
        for (FeedLatencyPoint point : snapshot.points()) {
            max = Math.max(max, point.p99Ms());
        }
        return max;
    }
}
