package com.finance.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void normalization_shouldBeLocaleSafeUnderTurkishLocale() {
        YahooBistMarketDataService service = new YahooBistMarketDataService(mock(Bist100UniverseService.class));

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("BIST100", ReflectionTestUtils.invokeMethod(service, "normalizeSymbol", "bist100"));
            assertEquals("1h", ReflectionTestUtils.invokeMethod(service, "normalizeInterval", "1H"));
            assertEquals("BIST100.IS", ReflectionTestUtils.invokeMethod(service, "toYahooTicker", "bist100"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void getCandles_rejectsUnsupportedSymbolWithTypedContract() {
        Bist100UniverseService universeService = mock(Bist100UniverseService.class);
        when(universeService.supportsSymbol("DOGEUSDT")).thenReturn(false);
        YahooBistMarketDataService service = new YahooBistMarketDataService(universeService);

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> service.getCandles("DOGEUSDT", "1D", "1h", null, null));

        assertEquals("invalid_market_symbol", exception.code());
        assertEquals("Invalid market symbol", exception.getMessage());
    }

    @Test
    void getCandles_rejectsInvalidRangeWithTypedContract() {
        Bist100UniverseService universeService = mock(Bist100UniverseService.class);
        when(universeService.supportsSymbol("AEFES")).thenReturn(true);
        YahooBistMarketDataService service = new YahooBistMarketDataService(universeService);

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> service.getCandles("AEFES", "5D", "1h", null, null));

        assertEquals("invalid_market_range", exception.code());
        assertEquals("Invalid market range", exception.getMessage());
    }
}
