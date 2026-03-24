package com.finance.core.service;

import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BinanceServiceTest {

    @Test
    void buildTickerPriceUri_shouldEncodeSymbolsJsonWithoutWhitespace() {
        URI uri = BinanceService.buildTickerPriceUri();

        assertThat(uri.toString())
                .isEqualTo(
                        "https://api.binance.com/api/v3/ticker/price?symbols=%5B%22BTCUSDT%22%2C%22ETHUSDT%22%2C%22SOLUSDT%22%2C%22AVAXUSDT%22%2C%22BNBUSDT%22%5D");
        assertThat(uri.toString()).doesNotContain(" ");
    }

    @Test
    void build24hTickerUri_shouldEncodeSymbolsJsonWithoutWhitespace() {
        URI uri = BinanceService.build24hTickerUri();

        assertThat(uri.toString())
                .isEqualTo(
                        "https://api.binance.com/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22%2C%22ETHUSDT%22%2C%22SOLUSDT%22%2C%22AVAXUSDT%22%2C%22BNBUSDT%22%5D");
        assertThat(uri.toString()).doesNotContain(" ");
        assertThat(uri.toString()).doesNotContain("[");
        assertThat(uri.toString()).doesNotContain("]");
    }

    @Test
    void normalization_shouldBeLocaleSafeUnderTurkishLocale() {
        BinanceService service = new BinanceService();

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertEquals("BNBUSDT", ReflectionTestUtils.invokeMethod(service, "normalizeTrackedSymbol", "bnbusdt"));
            assertEquals("1h", ReflectionTestUtils.invokeMethod(service, "normalizeInterval", "1H"));
            Object queryConfig = ReflectionTestUtils.invokeMethod(service, "mapCandleQuery", "all", "1h", null, null);
            assertNull(ReflectionTestUtils.invokeMethod(queryConfig, "endTime"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void getCandles_rejectsUnsupportedSymbolWithTypedContract() {
        BinanceService service = new BinanceService();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> service.getCandles("DOGEUSDT", "1D", "1h", null, null));

        assertEquals("invalid_market_symbol", exception.code());
        assertEquals("Invalid market symbol", exception.getMessage());
    }

    @Test
    void seedTrackedPrice_rejectsNonPositivePriceWithTypedContract() {
        BinanceService service = new BinanceService();

        ApiRequestException exception = assertThrows(ApiRequestException.class,
                () -> service.seedTrackedPrice("BTCUSDT", 0.0));

        assertEquals("invalid_market_price", exception.code());
        assertEquals("Invalid market price", exception.getMessage());
    }
}
