package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketInstrumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooBistMarketDataService {

    private static final String YAHOO_QUOTE_URL = "https://query1.finance.yahoo.com/v7/finance/quote";
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final List<String> SUPPORTED_INTERVALS = List.of("1m", "15m", "30m", "1h", "4h", "1d");
    private static final Duration SNAPSHOT_CACHE_TTL = Duration.ofMinutes(10);

    private final Bist100UniverseService universeService;
    private final RestClient restClient = RestClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
            .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,tr;q=0.8")
            .defaultHeader(HttpHeaders.REFERER, "https://finance.yahoo.com/")
            .build();
    private final Map<String, CachedSnapshot> snapshotCache = new ConcurrentHashMap<>();

    public List<MarketInstrumentResponse> getSupportedInstruments() {
        return getInstrumentSnapshots(universeService.getSymbols()).values().stream().toList();
    }

    public Map<String, MarketInstrumentResponse> getInstrumentSnapshots(Collection<String> symbols) {
        List<String> normalizedSymbols = symbols.stream()
                .map(this::normalizeSymbol)
                .filter(universeService::supportsSymbol)
                .distinct()
                .toList();
        if (normalizedSymbols.isEmpty()) {
            return Map.of();
        }

        Map<String, MarketInstrumentResponse> snapshots = fetchQuoteSnapshots(normalizedSymbols);
        List<String> missingSymbols = normalizedSymbols.stream()
                .filter(symbol -> isMissingSnapshot(snapshots.get(symbol)))
                .toList();
        if (!missingSymbols.isEmpty()) {
            log.info("Yahoo BIST quote response missing live data for {} symbols; applying chart fallback", missingSymbols.size());
            missingSymbols.parallelStream()
                    .forEach(symbol -> {
                        MarketInstrumentResponse fallback = loadChartFallbackSnapshot(symbol);
                        if (fallback != null) {
                            snapshots.put(symbol, fallback);
                        }
                    });
        }

        Map<String, MarketInstrumentResponse> ordered = new LinkedHashMap<>();
        for (String symbol : normalizedSymbols) {
            MarketInstrumentResponse snapshot = snapshots.get(symbol);
            if (snapshot == null) {
                snapshot = zeroSnapshot(symbol);
            }
            ordered.put(symbol, snapshot);
        }
        return ordered;
    }

    private Map<String, MarketInstrumentResponse> fetchQuoteSnapshots(List<String> normalizedSymbols) {
        try {
            JsonNode response = restClient.get()
                    .uri(buildQuoteUri(normalizedSymbols))
                    .retrieve()
                    .body(JsonNode.class);

            Map<String, MarketInstrumentResponse> snapshots = new LinkedHashMap<>();
            JsonNode quoteResponse = response == null ? null : response.path("quoteResponse");
            JsonNode results = quoteResponse == null ? null : quoteResponse.path("result");
            JsonNode error = quoteResponse == null ? null : quoteResponse.path("error");
            if (error != null && !error.isMissingNode() && !error.isNull()) {
                log.warn("Yahoo BIST quote response returned error payload: {}", error);
            }
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    String yahooSymbol = result.path("symbol").asText("");
                    String symbol = yahooSymbol.replace(".IS", "").toUpperCase();
                    if (!normalizedSymbols.contains(symbol)) {
                        continue;
                    }
                    MarketInstrumentResponse snapshot = MarketInstrumentResponse.builder()
                            .symbol(symbol)
                            .displayName(resolveDisplayName(symbol, result))
                            .assetType("EQUITY")
                            .market("BIST100")
                            .exchange("BIST")
                            .currency("TRY")
                            .sector(resolveSector(symbol))
                            .delayLabel("Delayed 15m")
                            .currentPrice(resolveQuotePrice(result))
                            .changePercent24h(resolveQuoteChangePercent(result))
                            .build();
                    snapshots.put(symbol, snapshot);
                    cacheSnapshot(snapshot);
                }
            }
            if ((results == null || !results.isArray() || results.isEmpty()) && response != null) {
                log.warn("Yahoo BIST quote response returned no results for {} symbols", normalizedSymbols.size());
            }
            return snapshots;
        } catch (Exception e) {
            log.warn("Yahoo BIST quote refresh failed: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    public List<MarketCandleResponse> getCandles(String symbol, String range, String interval, Long beforeOpenTime, Integer limit) {
        String normalizedSymbol = normalizeSymbol(symbol);
        if (!universeService.supportsSymbol(normalizedSymbol)) {
            throw new IllegalArgumentException("Unsupported BIST100 symbol: " + symbol);
        }
        String normalizedInterval = normalizeInterval(interval);
        CandleQuery query = mapQuery(range, normalizedInterval, beforeOpenTime, limit);

        JsonNode response = restClient.get()
                .uri(buildChartUri(normalizedSymbol, query))
                .retrieve()
                .body(JsonNode.class);

        JsonNode result = response == null ? null : response.path("chart").path("result");
        if (result == null || !result.isArray() || result.isEmpty()) {
            return List.of();
        }
        JsonNode chart = result.get(0);
        List<MarketCandleResponse> candles = extractCandles(normalizedSymbol, chart);
        return candles;
    }

    private URI buildQuoteUri(List<String> symbols) {
        return UriComponentsBuilder.fromHttpUrl(YAHOO_QUOTE_URL)
                .queryParam("symbols", symbols.stream().map(this::toYahooTicker).reduce((a, b) -> a + "," + b).orElse(""))
                .queryParam("formatted", "false")
                .queryParam("lang", "en-US")
                .queryParam("region", "TR")
                .queryParam("corsDomain", "finance.yahoo.com")
                .build()
                .encode()
                .toUri();
    }

    private URI buildChartUri(String symbol, CandleQuery query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(YAHOO_CHART_URL + toYahooTicker(symbol))
                .queryParam("interval", query.yahooInterval())
                .queryParam("includePrePost", "false")
                .queryParam("events", "div,splits");

        if (query.range() != null) {
            builder.queryParam("range", query.range());
        } else {
            builder.queryParam("period1", query.period1EpochSeconds())
                    .queryParam("period2", query.period2EpochSeconds());
        }

        return builder.build().encode().toUri();
    }

    private CandleQuery mapQuery(String range, String interval, Long beforeOpenTime, Integer limit) {
        if (beforeOpenTime != null) {
            long period2 = Math.max(1L, (beforeOpenTime / 1000) - 1);
            long period1 = Math.max(1L, period2 - (long) normalizeLimit(limit, 500) * secondsPerInterval(interval));
            return new CandleQuery(toYahooInterval(interval), null, period1, period2);
        }

        String normalizedRange = range == null ? "1D" : range.trim().toUpperCase();
        if ("ALL".equals(normalizedRange)) {
            long period2 = Instant.now().getEpochSecond();
            long period1 = Math.max(1L, period2 - (long) normalizeLimit(limit, 500) * secondsPerInterval(interval));
            return new CandleQuery(toYahooInterval(interval), null, period1, period2);
        }

        String yahooRange = switch (normalizedRange) {
            case "1D" -> "1d";
            case "1W" -> "7d";
            case "1M" -> "1mo";
            case "3M" -> "3mo";
            case "6M" -> "6mo";
            case "1Y" -> "1y";
            default -> throw new IllegalArgumentException("Unsupported range: " + normalizedRange);
        };
        return new CandleQuery(toYahooInterval(interval), yahooRange, null, null);
    }

    private String toYahooInterval(String interval) {
        return switch (interval) {
            case "1m", "15m", "30m", "1h", "1d" -> interval;
            case "4h" -> "1h";
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
    }

    private long secondsPerInterval(String interval) {
        return switch (interval) {
            case "1m" -> 60L;
            case "15m" -> 900L;
            case "30m" -> 1800L;
            case "1h" -> 3600L;
            case "4h" -> 14400L;
            case "1d" -> 86400L;
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
    }

    private int normalizeLimit(Integer limit, int fallback) {
        if (limit == null || limit <= 0) {
            return fallback;
        }
        return Math.min(limit, 1000);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    private String normalizeInterval(String interval) {
        String normalized = interval == null ? "1h" : interval.trim().toLowerCase();
        if (!SUPPORTED_INTERVALS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported interval: " + interval);
        }
        return normalized;
    }

    private String toYahooTicker(String symbol) {
        return normalizeSymbol(symbol) + ".IS";
    }

    private String resolveDisplayName(String symbol, JsonNode result) {
        String longName = result.path("longName").asText("");
        if (!longName.isBlank()) {
            return longName;
        }
        String shortName = result.path("shortName").asText("");
        if (!shortName.isBlank()) {
            return shortName;
        }
        return symbol;
    }

    private double resolveQuotePrice(JsonNode result) {
        for (String field : List.of("regularMarketPrice", "postMarketPrice", "preMarketPrice", "bid", "ask", "regularMarketPreviousClose")) {
            double value = result.path(field).asDouble(0.0);
            if (value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private double resolveQuoteChangePercent(JsonNode result) {
        for (String field : List.of("regularMarketChangePercent", "postMarketChangePercent", "preMarketChangePercent")) {
            JsonNode node = result.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                return node.asDouble(0.0);
            }
        }

        double price = resolveQuotePrice(result);
        double previousClose = result.path("regularMarketPreviousClose").asDouble(0.0);
        if (price > 0.0 && previousClose > 0.0) {
            return ((price - previousClose) / previousClose) * 100.0;
        }
        return 0.0;
    }

    private boolean isMissingSnapshot(MarketInstrumentResponse snapshot) {
        return snapshot == null || snapshot.getCurrentPrice() <= 0.0;
    }

    private MarketInstrumentResponse loadChartFallbackSnapshot(String symbol) {
        CachedSnapshot cached = snapshotCache.get(symbol);
        if (cached != null && !cached.isExpired()) {
            return cached.snapshot();
        }

        try {
            JsonNode response = restClient.get()
                    .uri(buildChartUri(symbol, new CandleQuery("1d", "5d", null, null)))
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode result = response == null ? null : response.path("chart").path("result");
            if (result == null || !result.isArray() || result.isEmpty()) {
                return cached != null ? cached.snapshot() : zeroSnapshot(symbol);
            }

            JsonNode chart = result.get(0);
            JsonNode meta = chart.path("meta");
            double currentPrice = firstPositive(
                    meta.path("regularMarketPrice").asDouble(0.0),
                    meta.path("chartPreviousClose").asDouble(0.0),
                    meta.path("previousClose").asDouble(0.0)
            );
            double previousClose = firstPositive(
                    meta.path("previousClose").asDouble(0.0),
                    meta.path("chartPreviousClose").asDouble(0.0)
            );
            double changePercent = 0.0;
            if (currentPrice > 0.0 && previousClose > 0.0) {
                changePercent = ((currentPrice - previousClose) / previousClose) * 100.0;
            }

            MarketInstrumentResponse snapshot = MarketInstrumentResponse.builder()
                    .symbol(symbol)
                    .displayName(resolveDisplayName(symbol, meta))
                    .assetType("EQUITY")
                    .market("BIST100")
                    .exchange("BIST")
                    .currency("TRY")
                    .sector(resolveSector(symbol))
                    .delayLabel("Delayed 15m")
                    .currentPrice(currentPrice)
                    .changePercent24h(changePercent)
                    .build();
            cacheSnapshot(snapshot);
            return snapshot;
        } catch (Exception e) {
            log.warn("Yahoo BIST chart fallback failed for {}: {}", symbol, e.getMessage());
            return cached != null ? cached.snapshot() : zeroSnapshot(symbol);
        }
    }

    private void cacheSnapshot(MarketInstrumentResponse snapshot) {
        if (snapshot == null || snapshot.getCurrentPrice() <= 0.0) {
            return;
        }
        snapshotCache.put(snapshot.getSymbol(), new CachedSnapshot(snapshot, Instant.now().plus(SNAPSHOT_CACHE_TTL)));
    }

    private double firstPositive(double... values) {
        for (double value : values) {
            if (value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private MarketInstrumentResponse zeroSnapshot(String symbol) {
        return MarketInstrumentResponse.builder()
                .symbol(symbol)
                .displayName(symbol)
                .assetType("EQUITY")
                .market("BIST100")
                .exchange("BIST")
                .currency("TRY")
                .sector(resolveSector(symbol))
                .delayLabel("Delayed 15m")
                .currentPrice(0.0)
                .changePercent24h(0.0)
                .build();
    }

    private String resolveSector(String symbol) {
        return switch (symbol) {
            case "AEFES", "CCOLA", "TABGD", "ULKER", "PETUN" -> "Consumer";
            case "AKBNK", "GARAN", "HALKB", "ISCTR", "SKBNK", "TSKB", "VAKBN", "YKBNK", "ISMEN" -> "Financials";
            case "ASELS", "MIATK", "ASTOR", "EUPWR", "GESAN", "KONTR", "PATEK", "REEDR", "YEOTK" -> "Technology";
            case "EREGL", "KRDMD", "BRSAN", "BTCIM", "CIMSA", "OYAKC", "SISE", "SASA", "KCAER", "BSOKE" -> "Industrials";
            case "PGSUS", "THYAO", "TAVHL", "TTRAK", "OTKAR", "TOASO", "DOAS", "FROTO", "KARSN" -> "Transport & Auto";
            case "EKGYO", "DAPGM", "ALARK", "TRALT" -> "Real Assets";
            case "ENERY", "ENJSA", "AKSEN", "ODAS", "ZOREN" -> "Energy";
            default -> "BIST100";
        };
    }

    List<MarketCandleResponse> extractCandles(String symbol, JsonNode chart) {
        JsonNode timestamps = chart.path("timestamp");
        JsonNode quote = chart.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return List.of();
        }
        JsonNode quoteData = quote.get(0);
        JsonNode opens = quoteData.path("open");
        JsonNode highs = quoteData.path("high");
        JsonNode lows = quoteData.path("low");
        JsonNode closes = quoteData.path("close");
        JsonNode volumes = quoteData.path("volume");
        JsonNode adjCloseNodes = chart.path("indicators").path("adjclose");
        JsonNode adjCloses = adjCloseNodes.isArray() && !adjCloseNodes.isEmpty()
                ? adjCloseNodes.get(0).path("adjclose")
                : null;

        List<MarketCandleResponse> candles = new ArrayList<>();
        double previousClose = 0.0;
        int synthesizedCount = 0;
        for (int i = 0; i < timestamps.size(); i++) {
            double close = firstPositive(nodeValue(closes, i), nodeValue(adjCloses, i), previousClose);
            if (close <= 0.0) {
                continue;
            }

            double open = firstPositive(nodeValue(opens, i), previousClose, close);
            double high = firstPositive(nodeValue(highs, i), open, close);
            double low = firstPositive(nodeValue(lows, i), open, close);
            if (high < low) {
                double swap = high;
                high = low;
                low = swap;
            }

            if (nodeValue(opens, i) <= 0.0 || nodeValue(highs, i) <= 0.0 || nodeValue(lows, i) <= 0.0) {
                synthesizedCount++;
            }

            candles.add(MarketCandleResponse.builder()
                    .openTime(timestamps.get(i).asLong() * 1000)
                    .open(open)
                    .high(Math.max(high, Math.max(open, close)))
                    .low(Math.min(low, Math.min(open, close)))
                    .close(close)
                    .volume(Math.max(0.0, nodeValue(volumes, i)))
                    .build());
            previousClose = close;
        }

        if (synthesizedCount > 0) {
            log.info("Yahoo BIST candle fallback synthesized {} bars for {}", synthesizedCount, symbol);
        }
        return candles;
    }

    private double nodeValue(JsonNode node, int index) {
        if (node == null || !node.isArray() || index >= node.size() || node.path(index).isNull()) {
            return 0.0;
        }
        return node.get(index).asDouble(0.0);
    }

    private record CandleQuery(String yahooInterval, String range, Long period1EpochSeconds, Long period2EpochSeconds) {
    }

    private record CachedSnapshot(MarketInstrumentResponse snapshot, Instant expiresAt) {
        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
