package com.finance.core.observability;

import java.time.LocalDateTime;

public record WebSocketCanarySnapshot(
        LocalDateTime checkedAt,
        boolean success,
        int consecutiveFailures,
        int consecutiveSuccesses,
        int warningConsecutiveFailureThreshold,
        int criticalFailureThreshold,
        int windowSize,
        int windowFailureCount,
        double windowFailureRatio,
        String alertState,
        long latencyMs,
        String wsUrl,
        String topicDestination,
        String userQueueDestination,
        boolean topicReceived,
        boolean userQueueReceived,
        String error
) {
    public static WebSocketCanarySnapshot empty() {
        return new WebSocketCanarySnapshot(
                null,
                false,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                "NONE",
                0L,
                "",
                "",
                "",
                false,
                false,
                null
        );
    }
}
