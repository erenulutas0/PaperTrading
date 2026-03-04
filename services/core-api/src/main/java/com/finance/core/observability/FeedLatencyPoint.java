package com.finance.core.observability;

public record FeedLatencyPoint(
        String uri,
        long requestCount,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double maxMs,
        boolean sufficientSamples,
        boolean warningBreach,
        boolean criticalBreach
) {
}
