package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketInstrumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooBistMarketDataService {

    private static final String YAHOO_QUOTE_URL = "https://query1.finance.yahoo.com/v7/finance/quote";
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final List<String> SUPPORTED_INTERVALS = List.of("1m", "15m", "30m", "1h", "4h", "1d");

    private final Bist100UniverseService universeService;
    private final RestClient restClient = RestClient.create();

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

        try {
            JsonNode response = restClient.get()
                    .uri(buildQuoteUri(normalizedSymbols))
                    .retrieve()
                    .body(JsonNode.class);

            Map<String, MarketInstrumentResponse> snapshots = new LinkedHashMap<>();
            JsonNode results = response == null ? null : response.path("quoteResponse").path("result");
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    String yahooSymbol = result.path("symbol").asText("");
                    String symbol = yahooSymbol.replace(".IS", "").toUpperCase();
                    if (!normalizedSymbols.contains(symbol)) {
                        continue;
                    }
                    snapshots.put(symbol, MarketInstrumentResponse.builder()
                            .symbol(symbol)
                            .displayName(resolveDisplayName(symbol, result))
                            .assetType("EQUITY")
                            .currentPrice(result.path("regularMarketPrice").asDouble(0.0))
                            .changePercent24h(result.path("regularMarketChangePercent").asDouble(0.0))
                            .build());
                }
            }

            for (String symbol : normalizedSymbols) {
                snapshots.putIfAbsent(symbol, MarketInstrumentResponse.builder()
                        .symbol(symbol)
                        .displayName(symbol)
                        .assetType("EQUITY")
                        .currentPrice(0.0)
                        .changePercent24h(0.0)
                        .build());
            }
            return snapshots;
        } catch (Exception e) {
            log.warn("Yahoo BIST quote refresh failed: {}", e.getMessage());
            Map<String, MarketInstrumentResponse> fallback = new LinkedHashMap<>();
            for (String symbol : normalizedSymbols) {
                fallback.put(symbol, MarketInstrumentResponse.builder()
                        .symbol(symbol)
                        .displayName(symbol)
                        .assetType("EQUITY")
                        .currentPrice(0.0)
                        .changePercent24h(0.0)
                        .build());
            }
            return fallback;
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

        List<MarketCandleResponse> candles = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            if (opens.path(i).isNull() || highs.path(i).isNull() || lows.path(i).isNull() || closes.path(i).isNull()) {
                continue;
            }
            candles.add(MarketCandleResponse.builder()
                    .openTime(timestamps.get(i).asLong() * 1000)
                    .open(opens.get(i).asDouble())
                    .high(highs.get(i).asDouble())
                    .low(lows.get(i).asDouble())
                    .close(closes.get(i).asDouble())
                    .volume(volumes.path(i).asDouble(0.0))
                    .build());
        }
        return candles;
    }

    private URI buildQuoteUri(List<String> symbols) {
        return UriComponentsBuilder.fromHttpUrl(YAHOO_QUOTE_URL)
                .queryParam("symbols", symbols.stream().map(this::toYahooTicker).reduce((a, b) -> a + "," + b).orElse(""))
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

    private record CandleQuery(String yahooInterval, String range, Long period1EpochSeconds, Long period2EpochSeconds) {
    }
}
