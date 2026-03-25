package com.finance.core.observability;

import com.finance.core.config.WebSocketCanaryProperties;
import com.finance.core.config.WebSocketRuntimeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.websocket.canary.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketCanaryService {

    private static final String COMPONENT = "websocket-canary";
    private static final String ALERT_KEY = "probe-failed";
    private static final String RECOVERY_ALERT_KEY = "probe-recovered";

    private final WebSocketCanaryProperties properties;
    private final WebSocketRuntimeProperties runtimeProperties;
    private final WebSocketCanaryClient canaryClient;
    private final MeterRegistry meterRegistry;
    private final OpsAlertPublisher opsAlertPublisher;

    private final AtomicReference<WebSocketCanarySnapshot> latestSnapshot =
            new AtomicReference<>(WebSocketCanarySnapshot.empty());
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger lastSuccessGauge = new AtomicInteger(0);
    private final AtomicReference<Double> lastLatencyGauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> successRatioGauge = new AtomicReference<>(0.0);
    private final AtomicReference<Double> windowFailureRatioGauge = new AtomicReference<>(0.0);
    private final AtomicInteger consecutiveFailuresGauge = new AtomicInteger(0);
    private final AtomicInteger alertStateGauge = new AtomicInteger(0);
    private final AtomicLong totalRuns = new AtomicLong(0);
    private final AtomicLong successfulRuns = new AtomicLong(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicReference<CanaryAlertState> alertState = new AtomicReference<>(CanaryAlertState.NONE);

    private final Object recentWindowLock = new Object();
    private final Deque<Boolean> recentResults = new ArrayDeque<>();

    @PostConstruct
    void registerMeters() {
        meterRegistry.gauge("app.websocket.canary.last.success", lastSuccessGauge);
        meterRegistry.gauge("app.websocket.canary.last.latency.ms", lastLatencyGauge, AtomicReference::get);
        meterRegistry.gauge("app.websocket.canary.consecutive.failures", consecutiveFailuresGauge);
        meterRegistry.gauge("app.websocket.canary.success.ratio", successRatioGauge, AtomicReference::get);
        meterRegistry.gauge("app.websocket.canary.window.failure.ratio", windowFailureRatioGauge, AtomicReference::get);
        meterRegistry.gauge("app.websocket.canary.alert.state", alertStateGauge);
    }

    @Scheduled(fixedDelayString = "${app.websocket.canary.interval:PT2M}")
    @SchedulerLock(name = "WebSocketCanaryService.runCanaryScheduled", lockAtMostFor = "PT3M", lockAtLeastFor = "PT5S")
    public void runCanaryScheduled() {
        runCanaryProbe();
    }

    public WebSocketCanarySnapshot getLatestSnapshot() {
        return latestSnapshot.get();
    }

    public WebSocketCanarySnapshot runCanaryProbe() {
        String wsUrl = properties.resolvedWsUrl();
        String topicDestination = properties.normalizedTopicDestination();
        String userQueueDestination = properties.normalizedUserQueueDestination();
        String userSubscribeDestination = properties.normalizedUserSubscribeDestination();
        String userId = UUID.randomUUID().toString();
        String probeId = UUID.randomUUID().toString();
        int criticalThreshold = properties.normalizedCriticalFailureThreshold();
        int warningThreshold = properties.normalizedWarningConsecutiveFailureThreshold();
        int recoverySuccessThreshold = properties.normalizedRecoverySuccessThreshold();
        int windowSize = properties.normalizedWindowSize();
        int minWindowSamples = properties.normalizedMinWindowSamples();
        double warningFailureRatioThreshold = properties.normalizedWarningFailureRatioThreshold();
        double criticalFailureRatioThreshold = properties.normalizedCriticalFailureRatioThreshold();

        WebSocketCanaryProbeRequest request = new WebSocketCanaryProbeRequest(
                probeId,
                userId,
                wsUrl,
                resolveHostHeader(),
                topicDestination,
                userQueueDestination,
                userSubscribeDestination,
                properties.normalizedConnectTimeout(),
                properties.normalizedMessageTimeout()
        );

        long startedNs = System.nanoTime();
        WebSocketCanaryProbeResult result = executeProbeSafely(request, startedNs);
        long runDurationNs = System.nanoTime() - startedNs;
        long runLatencyMs = result.latencyMs() > 0 ? result.latencyMs() : runDurationNs / 1_000_000;
        boolean success = result.success();

        totalRuns.incrementAndGet();
        int currentFailures;
        int currentSuccesses;
        if (success) {
            successfulRuns.incrementAndGet();
            consecutiveFailures.set(0);
            currentFailures = 0;
            currentSuccesses = consecutiveSuccesses.incrementAndGet();
            lastSuccessGauge.set(1);
        } else {
            currentFailures = consecutiveFailures.incrementAndGet();
            consecutiveSuccesses.set(0);
            currentSuccesses = 0;
            lastSuccessGauge.set(0);
        }

        CanaryAlertState previousState = alertState.get();
        WindowStats windowStats = appendWindowAndCollectStats(success, windowSize);
        CanaryAlertState desiredState = evaluateDesiredState(
                currentFailures,
                currentSuccesses,
                windowStats.failureRatio(),
                windowStats.samples(),
                warningThreshold,
                criticalThreshold,
                warningFailureRatioThreshold,
                criticalFailureRatioThreshold,
                minWindowSamples,
                previousState,
                recoverySuccessThreshold
        );

        transitionAlertStateIfNeeded(previousState, desiredState, result, request, currentFailures, currentSuccesses,
                windowStats, warningThreshold, criticalThreshold);

        consecutiveFailuresGauge.set(currentFailures);
        lastLatencyGauge.set((double) runLatencyMs);
        successRatioGauge.set(computeSuccessRatio());
        windowFailureRatioGauge.set(windowStats.failureRatio());
        alertStateGauge.set(desiredState.gaugeValue());

        meterRegistry.counter("app.websocket.canary.runs.total", "result", success ? "success" : "failed")
                .increment();
        Timer.builder("app.websocket.canary.run.latency")
                .tag("result", success ? "success" : "failed")
                .register(meterRegistry)
                .record(runDurationNs, java.util.concurrent.TimeUnit.NANOSECONDS);

        WebSocketCanarySnapshot snapshot = new WebSocketCanarySnapshot(
                LocalDateTime.now(),
                success,
                currentFailures,
                currentSuccesses,
                warningThreshold,
                criticalThreshold,
                windowStats.samples(),
                windowStats.failures(),
                windowStats.failureRatio(),
                desiredState.name(),
                runLatencyMs,
                wsUrl,
                topicDestination,
                userQueueDestination,
                result.topicReceived(),
                result.userQueueReceived(),
                result.error()
        );
        latestSnapshot.set(snapshot);

        return snapshot;
    }

    private void publishFailureAlert(WebSocketCanaryProbeResult result,
                                     WebSocketCanaryProbeRequest request,
                                     OpsAlertSeverity severity,
                                     int failures,
                                     int successes,
                                     WindowStats windowStats,
                                     int warningThreshold,
                                     int criticalThreshold) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("wsUrl", request.wsUrl());
        details.put("topicDestination", request.topicDestination());
        details.put("userQueueDestination", request.userQueueDestination());
        details.put("topicReceived", result.topicReceived());
        details.put("userQueueReceived", result.userQueueReceived());
        details.put("latencyMs", result.latencyMs());
        details.put("error", result.error() == null ? "" : result.error());
        details.put("consecutiveFailures", failures);
        details.put("consecutiveSuccesses", successes);
        details.put("warningFailureThreshold", warningThreshold);
        details.put("criticalFailureThreshold", criticalThreshold);
        details.put("windowSamples", windowStats.samples());
        details.put("windowFailures", windowStats.failures());
        details.put("windowFailureRatio", windowStats.failureRatio());

        opsAlertPublisher.publish(
                COMPONENT,
                severity,
                ALERT_KEY,
                "Synthetic websocket canary probe failed",
                details
        );
    }

    private WebSocketCanaryProbeResult executeProbeSafely(WebSocketCanaryProbeRequest request, long startedNs) {
        try {
            WebSocketCanaryProbeResult result = canaryClient.probe(request);
            if (result == null) {
                return new WebSocketCanaryProbeResult(
                        false,
                        false,
                        elapsedMillis(startedNs),
                        "null-probe-result"
                );
            }
            return result;
        } catch (Exception ex) {
            log.warn("WebSocket canary probe raised exception: {}", ex.getMessage());
            log.debug("WebSocket canary probe stacktrace", ex);
            return new WebSocketCanaryProbeResult(
                    false,
                    false,
                    elapsedMillis(startedNs),
                    normalizeError(ex)
            );
        }
    }

    private double computeSuccessRatio() {
        long runs = totalRuns.get();
        if (runs <= 0) {
            return 0.0;
        }
        return ((double) successfulRuns.get()) / runs;
    }

    private void publishRecoveryAlert(WebSocketCanaryProbeRequest request,
                                      int successes,
                                      WindowStats windowStats,
                                      CanaryAlertState previousState) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("wsUrl", request.wsUrl());
        details.put("topicDestination", request.topicDestination());
        details.put("userQueueDestination", request.userQueueDestination());
        details.put("consecutiveSuccesses", successes);
        details.put("windowSamples", windowStats.samples());
        details.put("windowFailures", windowStats.failures());
        details.put("windowFailureRatio", windowStats.failureRatio());
        details.put("recoveredFrom", previousState.name());

        opsAlertPublisher.publish(
                COMPONENT,
                OpsAlertSeverity.WARNING,
                RECOVERY_ALERT_KEY,
                "Synthetic websocket canary recovered",
                details
        );
    }

    private long elapsedMillis(long startedNs) {
        return Math.max(0L, (System.nanoTime() - startedNs) / 1_000_000);
    }

    private String normalizeError(Throwable throwable) {
        if (throwable == null) {
            return "unknown-error";
        }
        String message = throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            message = throwable.getClass().getSimpleName();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) : singleLine;
    }

    private WindowStats appendWindowAndCollectStats(boolean success, int windowSize) {
        synchronized (recentWindowLock) {
            recentResults.addLast(success);
            while (recentResults.size() > windowSize) {
                recentResults.removeFirst();
            }

            int failures = 0;
            for (Boolean item : recentResults) {
                if (Boolean.FALSE.equals(item)) {
                    failures++;
                }
            }
            int samples = recentResults.size();
            double ratio = samples <= 0 ? 0.0 : ((double) failures) / samples;
            return new WindowStats(samples, failures, ratio);
        }
    }

    private CanaryAlertState evaluateDesiredState(int failures,
                                                  int successes,
                                                  double windowFailureRatio,
                                                  int windowSamples,
                                                  int warningThreshold,
                                                  int criticalThreshold,
                                                  double warningRatio,
                                                  double criticalRatio,
                                                  int minWindowSamples,
                                                  CanaryAlertState previousState,
                                                  int recoverySuccessThreshold) {
        boolean ratioEligible = windowSamples >= minWindowSamples;
        boolean critical = failures >= criticalThreshold || (ratioEligible && windowFailureRatio >= criticalRatio);
        boolean warning = failures >= warningThreshold || (ratioEligible && windowFailureRatio >= warningRatio);

        CanaryAlertState candidate = critical
                ? CanaryAlertState.CRITICAL
                : (warning ? CanaryAlertState.WARNING : CanaryAlertState.NONE);

        if (candidate == CanaryAlertState.NONE
                && previousState != CanaryAlertState.NONE
                && successes < recoverySuccessThreshold) {
            return previousState;
        }
        return candidate;
    }

    private void transitionAlertStateIfNeeded(CanaryAlertState previousState,
                                              CanaryAlertState desiredState,
                                              WebSocketCanaryProbeResult result,
                                              WebSocketCanaryProbeRequest request,
                                              int failures,
                                              int successes,
                                              WindowStats windowStats,
                                              int warningThreshold,
                                              int criticalThreshold) {
        if (desiredState == previousState) {
            return;
        }

        meterRegistry.counter(
                "app.websocket.canary.state.transitions",
                "from", previousState.name().toLowerCase(Locale.ROOT),
                "to", desiredState.name().toLowerCase(Locale.ROOT)
        ).increment();

        alertState.set(desiredState);

        if (desiredState == CanaryAlertState.NONE) {
            log.info("WebSocket canary recovered. previousState={} consecutiveSuccesses={} windowFailureRatio={}",
                    previousState, successes, windowStats.failureRatio());
            if (properties.isAlertOnRecovery()) {
                publishRecoveryAlert(request, successes, windowStats, previousState);
            }
            return;
        }

        log.warn("WebSocket canary state transition: {} -> {} (failures={} successes={} windowFailureRatio={} error={})",
                previousState, desiredState, failures, successes, windowStats.failureRatio(), result.error());

        publishFailureAlert(
                result,
                request,
                desiredState.toSeverity(),
                failures,
                successes,
                windowStats,
                warningThreshold,
                criticalThreshold
        );
    }

    private String resolveHostHeader() {
        if (!runtimeProperties.isRelayBrokerMode()) {
            return null;
        }
        String virtualHost = runtimeProperties.getRelayVirtualHost();
        if (!StringUtils.hasText(virtualHost)) {
            return null;
        }
        return virtualHost.trim();
    }

    private enum CanaryAlertState {
        NONE(0),
        WARNING(1),
        CRITICAL(2);

        private final int gaugeValue;

        CanaryAlertState(int gaugeValue) {
            this.gaugeValue = gaugeValue;
        }

        int gaugeValue() {
            return gaugeValue;
        }

        OpsAlertSeverity toSeverity() {
            return this == CRITICAL ? OpsAlertSeverity.CRITICAL : OpsAlertSeverity.WARNING;
        }
    }

    private record WindowStats(int samples, int failures, double failureRatio) {
    }
}
