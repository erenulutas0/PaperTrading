package com.finance.core.observability;

public record WebSocketCanaryProbeResult(
        boolean topicReceived,
        boolean userQueueReceived,
        long latencyMs,
        String error
) {
    public boolean success() {
        return topicReceived && userQueueReceived && error == null;
    }
}
