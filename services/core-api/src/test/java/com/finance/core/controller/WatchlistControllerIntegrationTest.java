package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Watchlist;
import com.finance.core.domain.WatchlistAlertDirection;
import com.finance.core.domain.WatchlistAlertEvent;
import com.finance.core.domain.WatchlistItem;
import com.finance.core.dto.MarketType;
import com.finance.core.dto.MarketCandleResponse;
import com.finance.core.dto.MarketInstrumentResponse;
import com.finance.core.repository.WatchlistAlertEventRepository;
import com.finance.core.repository.WatchlistItemRepository;
import com.finance.core.repository.WatchlistRepository;
import com.finance.core.service.MarketDataFacadeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.time.LocalDateTime;
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
        @Autowired
        private WatchlistAlertEventRepository watchlistAlertEventRepository;
        @MockitoBean
        private MarketDataFacadeService marketDataFacadeService;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private JdbcTemplate jdbcTemplate;

        private UUID userId = UUID.randomUUID();
        private Watchlist testWatchlist;

        @BeforeEach
        void setUp() {
                watchlistItemRepository.deleteAll();
                watchlistAlertEventRepository.deleteAll();
                watchlistRepository.deleteAll();

                testWatchlist = Watchlist.builder()
                                .name("Integration Watchlist")
                                .userId(userId)
                                .items(new ArrayList<>())
                                .build();
                testWatchlist = watchlistRepository.save(testWatchlist);

                when(marketDataFacadeService.getInstrumentSnapshots(java.util.List.of("BTCUSDT"))).thenReturn(Map.of(
                                "BTCUSDT", MarketInstrumentResponse.builder()
                                                .symbol("BTCUSDT")
                                                .displayName("Bitcoin")
                                                .assetType("CRYPTO")
                                                .currentPrice(60000.0)
                                                .changePercent24h(3.4)
                                                .build()));
                when(marketDataFacadeService.getSupportedInstruments(MarketType.CRYPTO)).thenReturn(java.util.List.of(
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
    void testGetAlertHistory() throws Exception {
        WatchlistItem item = watchlistItemRepository.save(WatchlistItem.builder()
                                .watchlist(testWatchlist)
                                .symbol("BTCUSDT")
                                .build());
                watchlistAlertEventRepository.save(WatchlistAlertEvent.builder()
                                .watchlistItem(item)
                                .userId(userId)
                                .symbol("BTCUSDT")
                                .direction(WatchlistAlertDirection.ABOVE)
                                .thresholdPrice(java.math.BigDecimal.valueOf(61000))
                                .triggeredPrice(java.math.BigDecimal.valueOf(61500))
                                .message("BTCUSDT hit above alert")
                                .build());

                mockMvc.perform(get("/api/v1/watchlists/items/" + item.getId() + "/alert-history")
                                .header("X-User-Id", userId.toString())
                                .param("limit", "5"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$", hasSize(1)))
                                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"))
                                .andExpect(jsonPath("$[0].direction").value("ABOVE"))
                                .andExpect(jsonPath("$[0].triggeredPrice").value(61500));
    }

    @Test
    void testGetAlertHistory_withDaysFilter() throws Exception {
        WatchlistItem item = watchlistItemRepository.save(WatchlistItem.builder()
                .watchlist(testWatchlist)
                .symbol("BTCUSDT")
                .build());
        watchlistAlertEventRepository.save(WatchlistAlertEvent.builder()
                .watchlistItem(item)
                .userId(userId)
                .symbol("BTCUSDT")
                .direction(WatchlistAlertDirection.ABOVE)
                .thresholdPrice(java.math.BigDecimal.valueOf(61000))
                .triggeredPrice(java.math.BigDecimal.valueOf(61500))
                .message("Recent trigger")
                .build());
        WatchlistAlertEvent olderEvent = watchlistAlertEventRepository.save(WatchlistAlertEvent.builder()
                .watchlistItem(item)
                .userId(userId)
                .symbol("BTCUSDT")
                .direction(WatchlistAlertDirection.BELOW)
                .thresholdPrice(java.math.BigDecimal.valueOf(59000))
                .triggeredPrice(java.math.BigDecimal.valueOf(58500))
                .message("Older trigger")
                .build());
        jdbcTemplate.update(
                "update watchlist_alert_events set triggered_at = ? where id = ?",
                LocalDateTime.now().minusDays(10),
                olderEvent.getId());

        mockMvc.perform(get("/api/v1/watchlists/items/" + item.getId() + "/alert-history")
                        .header("X-User-Id", userId.toString())
                        .param("limit", "10")
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message").value("Recent trigger"));
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
                when(marketDataFacadeService.getCandles(MarketType.CRYPTO, "BTCUSDT", "ALL", "15m", null, 500)).thenReturn(java.util.List.of(
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
                when(marketDataFacadeService.getCandles(MarketType.CRYPTO, "BTCUSDT", "1Y", "1d", null, null)).thenReturn(java.util.List.of(
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
