package com.finance.core.observability;

public interface WebSocketCanaryClient {

    WebSocketCanaryProbeResult probe(WebSocketCanaryProbeRequest request);
}
