package com.finance.core.observability;

import java.time.LocalDateTime;
import java.util.List;

public record FeedLatencySnapshot(
        LocalDateTime checkedAt,
        long minSamples,
        double warningP95Ms,
        double warningP99Ms,
        double criticalP99Ms,
        int warningBreaches,
        int criticalBreaches,
        List<FeedLatencyPoint> points,
        String error
) {
    public boolean available() {
        return error == null;
    }

    public boolean hasCriticalBreach() {
        return criticalBreaches > 0;
    }

    public static FeedLatencySnapshot empty() {
        return new FeedLatencySnapshot(
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                null
        );
    }
}
