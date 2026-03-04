package com.finance.core.observability;

import java.time.Duration;

public record WebSocketCanaryProbeRequest(
        String probeId,
        String userId,
        String wsUrl,
        String hostHeader,
        String topicDestination,
        String userQueueDestination,
        String userSubscribeDestination,
        Duration connectTimeout,
        Duration messageTimeout
) {
}
