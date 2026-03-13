package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.dto.MarketCandleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class YahooBistMarketDataServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractCandles_shouldSynthesizeMissingOhlcFromCloseSeries() throws Exception {
        YahooBistMarketDataService service = new YahooBistMarketDataService(mock(Bist100UniverseService.class));
        JsonNode chart = objectMapper.readTree("""
                {
                  "timestamp": [1710000000, 1710003600],
                  "indicators": {
                    "quote": [{
                      "open": [null, null],
                      "high": [null, null],
                      "low": [null, null],
                      "close": [10.5, 11.0],
                      "volume": [1200, 900]
                    }]
                  }
                }
                """);

        List<MarketCandleResponse> candles = service.extractCandles("AEFES", chart);

        assertEquals(2, candles.size());
        assertEquals(10.5, candles.get(0).getOpen());
        assertEquals(10.5, candles.get(0).getHigh());
        assertEquals(10.5, candles.get(0).getLow());
        assertEquals(10.5, candles.get(0).getClose());
        assertEquals(10.5, candles.get(1).getOpen());
        assertEquals(11.0, candles.get(1).getClose());
        assertEquals(11.0, candles.get(1).getHigh());
        assertEquals(10.5, candles.get(1).getLow());
    }
}
