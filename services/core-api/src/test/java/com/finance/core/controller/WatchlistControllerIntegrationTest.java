package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Watchlist;
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
                                .andExpect(jsonPath("$[0].currentPrice").value(60000.0));
        }
}
