package com.finance.core.observability;

import java.time.LocalDateTime;
import java.util.Map;

public record WebSocketObservabilitySnapshot(
        LocalDateTime checkedAt,
        int activeSessions,
        long connectEvents,
        long disconnectEvents,
        long stompErrorEvents,
        long reconnectCandidates,
        long reconnectSuccesses,
        double reconnectSuccessRatio,
        Map<String, Long> stompErrorsByCommand
) {
}
