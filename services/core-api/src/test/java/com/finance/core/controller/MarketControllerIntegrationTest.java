package com.finance.core.controller;

import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketType;
import com.finance.core.service.BinanceService;
import com.finance.core.service.MarketDataFacadeService;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MarketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketDataFacadeService marketDataFacadeService;

    @MockitoBean
    private BinanceService binanceService;

    @Test
    void getPrices_rejectsInvalidMarketTypeWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/prices").param("market", "stocks"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_type"))
                .andExpect(jsonPath("$.message").value("Invalid market type"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getSupportedInstruments_rejectsInvalidMarketTypeWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/instruments").param("market", "fx"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_type"))
                .andExpect(jsonPath("$.message").value("Invalid market type"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_rejectsBlankSymbolWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles").param("symbol", " "))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("market_symbol_required"))
                .andExpect(jsonPath("$.message").value("Market symbol is required"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_missingSymbol_shouldReturnCorrelatedMissingRequestParameterContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .header("X-Request-Id", "market-missing-param-1"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "market-missing-param-1"))
                .andExpect(jsonPath("$.code").value("missing_request_parameter"))
                .andExpect(jsonPath("$.message").value("Missing required request parameter"))
                .andExpect(jsonPath("$.details.parameter").value("symbol"))
                .andExpect(jsonPath("$.requestId").value("market-missing-param-1"));
    }

    @Test
    void getCandles_rejectsInvalidRangeWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("range", "5D"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_range"))
                .andExpect(jsonPath("$.message").value("Invalid market range"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_rejectsInvalidIntervalWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("interval", "2h"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_interval"))
                .andExpect(jsonPath("$.message").value("Invalid market interval"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_rejectsInvalidLimitWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_limit"))
                .andExpect(jsonPath("$.message").value("Invalid market limit"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_rejectsNonNumericLimitWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("limit", "many"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_limit"))
                .andExpect(jsonPath("$.message").value("Invalid market limit"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_rejectsNonNumericBeforeOpenTimeWithExplicitContract() throws Exception {
        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .param("beforeOpenTime", "yesterday"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_before_open_time"))
                .andExpect(jsonPath("$.message").value("Invalid market beforeOpenTime"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_mapsUnsupportedSymbolToExplicitContract() throws Exception {
        when(marketDataFacadeService.getCandles(
                eq(MarketType.CRYPTO),
                eq("DOGEUSDT"),
                eq("1D"),
                eq("1h"),
                isNull(),
                isNull()))
                .thenThrow(ApiRequestException.badRequest("invalid_market_symbol", "Invalid market symbol"));

        mockMvc.perform(get("/api/v1/market/candles").param("symbol", "DOGEUSDT"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("invalid_market_symbol"))
                .andExpect(jsonPath("$.message").value("Invalid market symbol"))
                .andExpect(jsonPath("$.requestId", not(isEmptyOrNullString())));
    }

    @Test
    void getCandles_whenUnexpectedValidationRuntimeOccurs_shouldReturnSharedGenericFallback() throws Exception {
        when(marketDataFacadeService.getCandles(
                eq(MarketType.CRYPTO),
                eq("BTCUSDT"),
                eq("1D"),
                eq("1h"),
                isNull(),
                isNull()))
                .thenThrow(new IllegalArgumentException("market internals leaked"));

        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "BTCUSDT")
                        .header("X-Request-Id", "market-fallback-1"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "market-fallback-1"))
                .andExpect(jsonPath("$.code").value("bad_request"))
                .andExpect(jsonPath("$.message").value("Unexpected error"))
                .andExpect(jsonPath("$.requestId").value("market-fallback-1"));
    }

    @Test
    void getCandles_returnsCandlesForValidRequest() throws Exception {
        when(marketDataFacadeService.getCandles(
                eq(MarketType.BIST100),
                eq("AKBNK"),
                eq("1W"),
                eq("1d"),
                isNull(),
                eq(100)))
                .thenReturn(List.of(MarketCandleResponse.builder()
                        .openTime(1710000000000L)
                        .open(40.5)
                        .high(41.2)
                        .low(39.8)
                        .close(40.9)
                        .volume(1250000)
                        .build()));

        mockMvc.perform(get("/api/v1/market/candles")
                        .param("symbol", "AKBNK")
                        .param("market", "BIST100")
                        .param("range", "1W")
                        .param("interval", "1d")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openTime").value(1710000000000L))
                .andExpect(jsonPath("$[0].close").value(40.9));
    }

    @Test
    void getPrices_defaultsMissingMarketToCrypto() throws Exception {
        when(marketDataFacadeService.getPrices(eq(MarketType.CRYPTO)))
                .thenReturn(Map.of("BTCUSDT", 65000.0));

        mockMvc.perform(get("/api/v1/market/prices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.BTCUSDT").value(65000.0));
    }
}
