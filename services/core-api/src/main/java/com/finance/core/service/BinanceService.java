package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BinanceService extends TextWebSocketHandler {

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@ticker/ethusdt@ticker/solusdt@ticker/avaxusdt@ticker/bnbusdt@ticker";
    @Value("${app.market.ws.enabled:true}")
    private boolean marketWsEnabled = true;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private BinanceService self;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start() {
        if (!marketWsEnabled) {
            log.info("Binance WebSocket startup disabled by configuration");
            return;
        }
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
            }
        } catch (Exception e) {
            log.warn("Error parsing message: {}", e.getMessage());
        }
    }

    public Map<String, Double> getPrices() {
        return prices;
    }
}
