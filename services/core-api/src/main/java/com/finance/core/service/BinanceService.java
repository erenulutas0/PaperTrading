package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketInstrumentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BinanceService extends TextWebSocketHandler {

    private final Map<String, Double> prices = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();
    private static final List<String> TRACKED_SYMBOLS = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT", "AVAXUSDT", "BNBUSDT");
    private static final Map<String, String> SYMBOL_DISPLAY_NAMES = Map.of(
            "BTCUSDT", "Bitcoin",
            "ETHUSDT", "Ethereum",
            "SOLUSDT", "Solana",
            "AVAXUSDT", "Avalanche",
            "BNBUSDT", "BNB");
    private static final String BINANCE_WS_URL = "wss://stream.binance.com:9443/stream?streams=btcusdt@ticker/ethusdt@ticker/solusdt@ticker/avaxusdt@ticker/bnbusdt@ticker";
    private static final String BINANCE_REST_BASE_URL = "https://api.binance.com/api/v3/ticker/price";
    private static final String BINANCE_REST_24H_BASE_URL = "https://api.binance.com/api/v3/ticker/24hr";
    private static final String BINANCE_REST_KLINES_BASE_URL = "https://api.binance.com/api/v3/klines";
    private static final List<String> SUPPORTED_INTERVALS = List.of("1m", "15m", "30m", "1h", "4h", "1d");
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

    public List<MarketInstrumentResponse> getSupportedInstruments() {
        Map<String, Double> livePrices = getPrices();
        Map<String, Double> dailyChanges = fetchTracked24hChangePercent();
        List<MarketInstrumentResponse> instruments = new ArrayList<>();
        for (String symbol : TRACKED_SYMBOLS) {
            instruments.add(MarketInstrumentResponse.builder()
                    .symbol(symbol)
                    .displayName(SYMBOL_DISPLAY_NAMES.getOrDefault(symbol, symbol))
                    .assetType("CRYPTO")
                    .currentPrice(livePrices.getOrDefault(symbol, 0.0))
                    .changePercent24h(dailyChanges.getOrDefault(symbol, 0.0))
                    .build());
        }
        return instruments;
    }

    public List<MarketCandleResponse> getCandles(
            String symbol,
            String range,
            String interval,
            Long beforeOpenTime,
            Integer limit) {
        String normalizedSymbol = normalizeTrackedSymbol(symbol);
        String normalizedInterval = normalizeInterval(interval);
        CandleQueryConfig config = mapCandleQuery(range, normalizedInterval, beforeOpenTime, limit);

        JsonNode response = restClient.get()
                .uri(buildKlinesUri(normalizedSymbol, config.interval(), config.limit(), config.endTime()))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.isArray()) {
            return List.of();
        }

        List<MarketCandleResponse> candles = new ArrayList<>();
        for (JsonNode row : response) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }
            candles.add(MarketCandleResponse.builder()
                    .openTime(row.get(0).asLong())
                    .open(Double.parseDouble(row.get(1).asText()))
                    .high(Double.parseDouble(row.get(2).asText()))
                    .low(Double.parseDouble(row.get(3).asText()))
                    .close(Double.parseDouble(row.get(4).asText()))
                    .volume(Double.parseDouble(row.get(5).asText()))
                    .build());
        }
        return candles;
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
                    .uri(buildTickerPriceUri())
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

    static URI buildTickerPriceUri() {
        try {
            String symbolsJson = new ObjectMapper().writeValueAsString(TRACKED_SYMBOLS);
            return UriComponentsBuilder.fromHttpUrl(BINANCE_REST_BASE_URL)
                    .queryParam("symbols", symbolsJson)
                    .build()
                    .encode()
                    .toUri();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Binance ticker price URI", e);
        }
    }

    private Map<String, Double> fetchTracked24hChangePercent() {
        try {
            JsonNode response = restClient.get()
                    .uri(build24hTickerUri())
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.isArray()) {
                return Map.of();
            }
            Map<String, Double> changes = new LinkedHashMap<>();
            for (JsonNode ticker : response) {
                String symbol = ticker.path("symbol").asText(null);
                String changePercent = ticker.path("priceChangePercent").asText(null);
                if (symbol == null || changePercent == null) {
                    continue;
                }
                try {
                    changes.put(symbol, Double.parseDouble(changePercent));
                } catch (NumberFormatException ignored) {
                    log.debug("Skipping unparsable Binance 24h change for symbol={} raw={}", symbol, changePercent);
                }
            }
            return changes;
        } catch (Exception e) {
            log.warn("Binance 24h ticker refresh failed: {}", e.getMessage());
            return Map.of();
        }
    }

    private URI build24hTickerUri() {
        try {
            String symbolsJson = mapper.writeValueAsString(TRACKED_SYMBOLS);
            return UriComponentsBuilder.fromHttpUrl(BINANCE_REST_24H_BASE_URL)
                    .queryParam("symbols", symbolsJson)
                    .build()
                    .encode()
                    .toUri();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Binance 24h ticker URI", e);
        }
    }

    private URI buildKlinesUri(String symbol, String interval, int limit, Long endTime) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BINANCE_REST_KLINES_BASE_URL)
                .queryParam("symbol", symbol)
                .queryParam("interval", interval)
                .queryParam("limit", limit);
        if (endTime != null) {
            builder.queryParam("endTime", endTime);
        }
        return builder.build().encode().toUri();
    }

    private String normalizeTrackedSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase();
        if (!TRACKED_SYMBOLS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported symbol: " + symbol);
        }
        return normalized;
    }

    private String normalizeInterval(String interval) {
        String normalized = interval == null ? "1h" : interval.trim().toLowerCase();
        if (!SUPPORTED_INTERVALS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }
        return normalized;
    }

    private CandleQueryConfig mapCandleQuery(String range, String interval, Long beforeOpenTime, Integer limit) {
        if (beforeOpenTime != null) {
            return new CandleQueryConfig(interval, normalizeLimit(limit, 500), beforeOpenTime - 1);
        }

        String normalizedRange = range == null ? "1D" : range.trim().toUpperCase();
        if ("ALL".equals(normalizedRange)) {
            return new CandleQueryConfig(interval, normalizeLimit(limit, 500), null);
        }

        int intervalMinutes = switch (interval) {
            case "1m" -> 1;
            case "15m" -> 15;
            case "30m" -> 30;
            case "1h" -> 60;
            case "4h" -> 240;
            case "1d" -> 1440;
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };

        int rangeMinutes = switch (normalizedRange) {
            case "1W" -> 7 * 24 * 60;
            case "1M" -> 30 * 24 * 60;
            case "3M" -> 90 * 24 * 60;
            case "6M" -> 180 * 24 * 60;
            case "1Y" -> 365 * 24 * 60;
            default -> 24 * 60;
        };

        int computedLimit = (int) Math.ceil((double) rangeMinutes / intervalMinutes);
        return new CandleQueryConfig(interval, Math.max(30, Math.min(1000, computedLimit)), null);
    }

    private int normalizeLimit(Integer requested, int fallback) {
        if (requested == null) {
            return fallback;
        }
        return Math.max(100, Math.min(1000, requested));
    }

    private record CandleQueryConfig(String interval, int limit, Long endTime) {
    }
}
