package com.finance.core.service;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

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
}
