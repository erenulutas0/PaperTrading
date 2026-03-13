package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Watchlist;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketInstrumentResponse;
import com.finance.core.repository.WatchlistItemRepository;
import com.finance.core.repository.WatchlistRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WatchlistControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private WatchlistRepository watchlistRepository;
        @Autowired
        private WatchlistItemRepository watchlistItemRepository;
        @MockitoBean
        private BinanceService binanceService;
        @Autowired
        private ObjectMapper objectMapper;

        private UUID userId = UUID.randomUUID();
        private Watchlist testWatchlist;

        @BeforeEach
        void setUp() {
                watchlistItemRepository.deleteAll();
                watchlistRepository.deleteAll();

                testWatchlist = Watchlist.builder()
                                .name("Integration Watchlist")
                                .userId(userId)
                                .items(new ArrayList<>())
                                .build();
                testWatchlist = watchlistRepository.save(testWatchlist);

                when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 60000.0));
                when(binanceService.getSupportedInstruments()).thenReturn(java.util.List.of(
                                MarketInstrumentResponse.builder()
                                                .symbol("BTCUSDT")
                                                .displayName("Bitcoin")
                                                .assetType("CRYPTO")
                                                .currentPrice(60000.0)
                                                .changePercent24h(3.4)
                                                .build()));
        }

        @Test
        void testCreateWatchlist() throws Exception {
                Map<String, String> body = Map.of("name", "New Watchlist");
                mockMvc.perform(post("/api/v1/watchlists")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("New Watchlist"))
                                .andExpect(jsonPath("$.userId").value(userId.toString()));
        }

        @Test
        void testAddItemToWatchlist() throws Exception {
                Map<String, Object> itemRequest = Map.of(
                                "symbol", "ETHUSDT",
                                "alertPriceAbove", 4000);

                mockMvc.perform(post("/api/v1/watchlists/" + testWatchlist.getId() + "/items")
                                .header("X-User-Id", userId.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(itemRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.symbol").value("ETHUSDT"))
                                .andExpect(jsonPath("$.alertPriceAbove").value(4000.0));
        }

        @Test
        void testGetWatchlistEnriched() throws Exception {
                // First add an item
                testWatchlist.getItems().add(com.finance.core.domain.WatchlistItem.builder()
                                .symbol("BTCUSDT")
                                .watchlist(testWatchlist)
                                .build());
                watchlistRepository.save(testWatchlist);

                mockMvc.perform(get("/api/v1/watchlists/" + testWatchlist.getId() + "/items")
                                .header("X-User-Id", userId.toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                                .andExpect(jsonPath("$[0].currentPrice").value(60000.0))
                                .andExpect(jsonPath("$[0].changePercent24h").value(3.4));
        }

        @Test
        void testGetInstruments() throws Exception {
                mockMvc.perform(get("/api/v1/market/instruments"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                                .andExpect(jsonPath("$[0].displayName").value("Bitcoin"));
        }

        @Test
        void testGetCandles() throws Exception {
                when(binanceService.getCandles("BTCUSDT", "ALL", "15m", null, 500)).thenReturn(java.util.List.of(
                                MarketCandleResponse.builder()
                                                .openTime(1710000000000L)
                                                .open(60000.0)
                                                .high(60500.0)
                                                .low(59800.0)
                                                .close(60300.0)
                                                .volume(123.45)
                                                .build()));

                mockMvc.perform(get("/api/v1/market/candles")
                                .param("symbol", "BTCUSDT")
                                .param("range", "ALL")
                                .param("interval", "15m")
                                .param("limit", "500"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].close").value(60300.0));
        }

        @Test
        void testGetCandles_withExtendedRange() throws Exception {
                when(binanceService.getCandles("BTCUSDT", "1Y", "1d", null, null)).thenReturn(java.util.List.of(
                                MarketCandleResponse.builder()
                                                .openTime(1710000000000L)
                                                .open(50000.0)
                                                .high(70000.0)
                                                .low(48000.0)
                                                .close(65000.0)
                                                .volume(456.78)
                                                .build()));

                mockMvc.perform(get("/api/v1/market/candles")
                                .param("symbol", "BTCUSDT")
                                .param("range", "1Y")
                                .param("interval", "1d"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].close").value(65000.0));
        }
}
