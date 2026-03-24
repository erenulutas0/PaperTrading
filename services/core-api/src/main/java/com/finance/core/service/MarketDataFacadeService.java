package com.finance.core.service;

import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketInstrumentResponse;
import com.finance.core.dto.MarketType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MarketDataFacadeService {

    private final BinanceService binanceService;
    private final YahooBistMarketDataService yahooBistMarketDataService;

    public Map<String, Double> getPrices(MarketType marketType) {
        return switch (marketType) {
            case CRYPTO -> binanceService.getPrices();
            case BIST100 -> yahooBistMarketDataService.getInstrumentSnapshots(yahooBistMarketDataService.getSupportedInstruments().stream().map(MarketInstrumentResponse::getSymbol).toList())
                    .values().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            MarketInstrumentResponse::getSymbol,
                            MarketInstrumentResponse::getCurrentPrice,
                            (left, right) -> right,
                            LinkedHashMap::new));
        };
    }

    public List<MarketInstrumentResponse> getSupportedInstruments(MarketType marketType) {
        return switch (marketType) {
            case CRYPTO -> binanceService.getSupportedInstruments();
            case BIST100 -> yahooBistMarketDataService.getSupportedInstruments();
        };
    }

    public List<MarketCandleResponse> getCandles(MarketType marketType, String symbol, String range, String interval, Long beforeOpenTime, Integer limit) {
        return switch (marketType) {
            case CRYPTO -> binanceService.getCandles(symbol, range, interval, beforeOpenTime, limit);
            case BIST100 -> yahooBistMarketDataService.getCandles(symbol, range, interval, beforeOpenTime, limit);
        };
    }

    public Map<String, MarketInstrumentResponse> getInstrumentSnapshots(Collection<String> symbols) {
        List<String> requested = symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(symbol -> symbol.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        Map<String, MarketInstrumentResponse> result = new LinkedHashMap<>();
        if (requested.isEmpty()) {
            return result;
        }

        Map<String, Double> cryptoPrices = binanceService.getPrices();
        Map<String, Double> cryptoChanges = binanceService.getSupportedInstruments().stream()
                .collect(java.util.stream.Collectors.toMap(
                        MarketInstrumentResponse::getSymbol,
                        MarketInstrumentResponse::getChangePercent24h,
                        (left, right) -> right,
                        LinkedHashMap::new));

        List<String> bistSymbols = new java.util.ArrayList<>();
        for (String symbol : requested) {
            if (cryptoPrices.containsKey(symbol)) {
                result.put(symbol, MarketInstrumentResponse.builder()
                        .symbol(symbol)
                        .displayName(symbol)
                        .assetType("CRYPTO")
                        .market("CRYPTO")
                        .exchange("BINANCE")
                        .currency("USDT")
                        .sector("Digital Asset")
                        .delayLabel("Real-time")
                        .currentPrice(cryptoPrices.getOrDefault(symbol, 0.0))
                        .changePercent24h(cryptoChanges.getOrDefault(symbol, 0.0))
                        .build());
            } else {
                bistSymbols.add(symbol);
            }
        }

        if (!bistSymbols.isEmpty()) {
            result.putAll(yahooBistMarketDataService.getInstrumentSnapshots(bistSymbols));
        }

        return result;
    }
}
