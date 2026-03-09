package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BinanceService extends TextWebSocketHandler {

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@ticker/ethusdt@ticker/solusdt@ticker/avaxusdt@ticker/bnbusdt@ticker";
    private static final String BINANCE_REST_URL = "https://api.binance.com/api/v3/ticker/price?symbols=%5B%22BTCUSDT%22,%22ETHUSDT%22,%22SOLUSDT%22,%22AVAXUSDT%22,%22BNBUSDT%22%5D";
    private static final Duration PRICE_STALE_AFTER = Duration.ofSeconds(20);
    private static final Duration FAILED_REFRESH_BACKOFF = Duration.ofSeconds(5);
    @Value("${app.market.ws.enabled:true}")
    private boolean marketWsEnabled = true;
    private volatile Instant lastPriceUpdateAt = Instant.EPOCH;
    private volatile Instant lastRestAttemptAt = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private BinanceService self;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start() {
        if (!marketWsEnabled) {
            log.info("Binance WebSocket startup disabled by configuration");
            refreshPricesFromRest("startup-ws-disabled");
            return;
        }
        refreshPricesFromRest("startup");
        self.connect();
    }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "binance")
    @io.github.resilience4j.retry.annotation.Retry(name = "binance", fallbackMethod = "connectFallback")
    public void connect() {
        try {
            StandardWebSocketClient client = new StandardWebSocketClient();
            client.execute(this, BINANCE_WS_URL).get(); // block to ensure exceptions are caught by circuit breaker
            log.info("Connected to Binance WebSocket: {}", BINANCE_WS_URL);
        } catch (Exception e) {
            log.error("Failed to connect to Binance WebSocket", e);
            throw new RuntimeException("WebSocket connection failed", e);
        }
    }

    public void connectFallback(Exception e) {
        log.error("Binance WebSocket fallback active. Max retries reached or circuit open", e);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            // Payload format: {"stream":"<streamName>","data":{...}}
            JsonNode root = mapper.readTree(message.getPayload());
            if (root.has("data")) {
                JsonNode data = root.get("data");
                String symbol = data.get("s").asText(); // Symbol
                double price = data.get("c").asDouble(); // Current Close Price
                prices.put(symbol, price);
                lastPriceUpdateAt = Instant.now();
            }
        } catch (Exception e) {
            log.warn("Error parsing message: {}", e.getMessage());
        }
    }

    public Map<String, Double> getPrices() {
        refreshPricesIfStale();
        return Map.copyOf(prices);
    }

    private void refreshPricesIfStale() {
        Instant now = Instant.now();
        if (!prices.isEmpty() && Duration.between(lastPriceUpdateAt, now).compareTo(PRICE_STALE_AFTER) < 0) {
            return;
        }
        if (Duration.between(lastRestAttemptAt, now).compareTo(FAILED_REFRESH_BACKOFF) < 0) {
            return;
        }

        synchronized (this) {
            Instant refreshedNow = Instant.now();
            if (!prices.isEmpty()
                    && Duration.between(lastPriceUpdateAt, refreshedNow).compareTo(PRICE_STALE_AFTER) < 0) {
                return;
            }
            if (Duration.between(lastRestAttemptAt, refreshedNow).compareTo(FAILED_REFRESH_BACKOFF) < 0) {
                return;
            }
            refreshPricesFromRest("stale-read");
        }
    }

    private void refreshPricesFromRest(String trigger) {
        lastRestAttemptAt = Instant.now();
        try {
            JsonNode response = restClient.get()
                    .uri(BINANCE_REST_URL)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.isArray()) {
                log.warn("Binance REST refresh returned unexpected payload on trigger={}", trigger);
                return;
            }

            int updated = 0;
            for (JsonNode ticker : response) {
                String symbol = ticker.path("symbol").asText(null);
                String priceRaw = ticker.path("price").asText(null);
                if (symbol == null || priceRaw == null) {
                    continue;
                }
                try {
                    prices.put(symbol, Double.parseDouble(priceRaw));
                    updated++;
                } catch (NumberFormatException ignored) {
                    log.debug("Skipping unparsable Binance REST price for symbol={} raw={}", symbol, priceRaw);
                }
            }

            if (updated > 0) {
                lastPriceUpdateAt = Instant.now();
                log.info("Binance REST refresh updated {} symbols on trigger={}", updated, trigger);
            }
        } catch (Exception e) {
            log.warn("Binance REST refresh failed on trigger={}: {}", trigger, e.getMessage());
        }
    }
}
