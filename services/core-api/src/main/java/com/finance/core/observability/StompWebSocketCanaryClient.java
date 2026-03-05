package com.finance.core.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@ConditionalOnProperty(name = "app.websocket.canary.enabled", havingValue = "true", matchIfMissing = true)
public class StompWebSocketCanaryClient implements WebSocketCanaryClient {

    private final SimpMessagingTemplate messagingTemplate;
    private final TaskScheduler receiptTaskScheduler;

    public StompWebSocketCanaryClient(
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("webSocketBrokerTaskScheduler") TaskScheduler receiptTaskScheduler
    ) {
        this.messagingTemplate = messagingTemplate;
        this.receiptTaskScheduler = receiptTaskScheduler;
    }

    @Override
    public WebSocketCanaryProbeResult probe(WebSocketCanaryProbeRequest request) {
        Instant startedAt = Instant.now();
        String topicPayload = "ws-canary-topic:" + request.probeId();
        String userPayload = "ws-canary-user:" + request.probeId();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        // Receipt tracking requires an explicit TaskScheduler on WebSocketStompClient.
        stompClient.setTaskScheduler(receiptTaskScheduler);
        long messageTimeoutMs = timeoutMillis(request.messageTimeout());
        stompClient.setReceiptTimeLimit(messageTimeoutMs);

        AtomicReference<String> transportError = new AtomicReference<>(null);
        AtomicReference<String> subscriptionReceiptWarning = new AtomicReference<>(null);
        CountDownLatch topicLatch = new CountDownLatch(1);
        CountDownLatch userLatch = new CountDownLatch(1);
        StompSession session = null;

        try {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("accept-version", "1.2");
            connectHeaders.add("X-User-Id", request.userId());
            if (StringUtils.hasText(request.hostHeader())) {
                connectHeaders.add("host", request.hostHeader());
            }

            StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
                @Override
                public void handleTransportError(StompSession stompSession, Throwable exception) {
                    transportError.compareAndSet(null, normalizeError(exception));
                }

                @Override
                public void handleException(StompSession session,
                                            StompCommand command,
                                            StompHeaders headers,
                                            byte[] payload,
                                            Throwable exception) {
                    transportError.compareAndSet(null, normalizeError(exception));
                }
            };

            CompletableFuture<StompSession> connectFuture = stompClient.connectAsync(
                    request.wsUrl(),
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    sessionHandler
            );
            session = connectFuture.get(timeoutMillis(request.connectTimeout()), TimeUnit.MILLISECONDS);
            session.setAutoReceipt(true);

            StompSession.Receiptable topicReceipt = session.subscribe(
                    request.topicDestination(),
                    new MatchingFrameHandler(topicPayload, topicLatch)
            );
            StompSession.Receiptable userReceipt = session.subscribe(
                    request.userSubscribeDestination(),
                    new MatchingFrameHandler(userPayload, userLatch)
            );

            CountDownLatch subscriptionReceipts = new CountDownLatch(2);
            attachSubscriptionReceipt(topicReceipt, "topic", subscriptionReceipts, subscriptionReceiptWarning);
            attachSubscriptionReceipt(userReceipt, "user", subscriptionReceipts, subscriptionReceiptWarning);

            boolean receiptsReady = subscriptionReceipts.await(messageTimeoutMs, TimeUnit.MILLISECONDS);
            if (!receiptsReady) {
                subscriptionReceiptWarning.compareAndSet(null, "subscription-receipt-timeout");
            }
            if (StringUtils.hasText(transportError.get())) {
                return new WebSocketCanaryProbeResult(false, false, elapsedMillis(startedAt), transportError.get());
            }

            messagingTemplate.convertAndSend(request.topicDestination(), topicPayload);
            messagingTemplate.convertAndSendToUser(request.userId(), request.userQueueDestination(), userPayload);

            boolean topicReceived = topicLatch.await(messageTimeoutMs, TimeUnit.MILLISECONDS);
            boolean userReceived = userLatch.await(messageTimeoutMs, TimeUnit.MILLISECONDS);
            long latencyMs = elapsedMillis(startedAt);

            String transportFailure = transportError.get();
            if (StringUtils.hasText(transportFailure)) {
                return new WebSocketCanaryProbeResult(topicReceived, userReceived, latencyMs, transportFailure);
            }

            // Treat receipt issues as soft warnings when payload delivery succeeds.
            if (topicReceived && userReceived) {
                return new WebSocketCanaryProbeResult(true, true, latencyMs, null);
            }

            String error = subscriptionReceiptWarning.get();
            if (!StringUtils.hasText(error)) {
                error = "message-delivery-timeout";
            }
            return new WebSocketCanaryProbeResult(topicReceived, userReceived, latencyMs, error);
        } catch (TimeoutException ex) {
            return new WebSocketCanaryProbeResult(false, false, elapsedMillis(startedAt), "timeout");
        } catch (Exception ex) {
            return new WebSocketCanaryProbeResult(false, false, elapsedMillis(startedAt), normalizeError(ex));
        } finally {
            disconnectQuietly(session);
            stopQuietly(stompClient);
        }
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private long timeoutMillis(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return 8000;
        }
        return Math.max(1000, duration.toMillis());
    }

    private void disconnectQuietly(StompSession session) {
        if (session == null) {
            return;
        }
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private void stopQuietly(WebSocketStompClient stompClient) {
        try {
            stompClient.stop();
        } catch (Exception ignored) {
        }
    }

    private String normalizeError(Throwable throwable) {
        if (throwable == null) {
            return "unknown-error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() > 200 ? singleLine.substring(0, 200) : singleLine;
    }

    private void attachSubscriptionReceipt(StompSession.Receiptable receiptable,
                                           String subscriptionName,
                                           CountDownLatch receiptLatch,
                                           AtomicReference<String> subscriptionReceiptWarning) {
        if (receiptable == null) {
            subscriptionReceiptWarning.compareAndSet(null, "subscription-receipt-unavailable:" + subscriptionName);
            receiptLatch.countDown();
            return;
        }

        String receiptId = receiptable.getReceiptId();
        if (!StringUtils.hasText(receiptId)) {
            subscriptionReceiptWarning.compareAndSet(null, "subscription-receipt-id-missing:" + subscriptionName);
            receiptLatch.countDown();
            return;
        }

        receiptable.addReceiptTask(receiptLatch::countDown);
        receiptable.addReceiptLostTask(() -> {
            subscriptionReceiptWarning.compareAndSet(null, "subscription-receipt-lost:" + subscriptionName);
            receiptLatch.countDown();
        });
    }

    private static class MatchingFrameHandler implements StompFrameHandler {

        private final String expectedPayload;
        private final CountDownLatch latch;

        private MatchingFrameHandler(String expectedPayload, CountDownLatch latch) {
            this.expectedPayload = expectedPayload;
            this.latch = latch;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            if (payload == null) {
                return;
            }
            if (expectedPayload.equals(payload.toString())) {
                latch.countDown();
            }
        }
    }
}
