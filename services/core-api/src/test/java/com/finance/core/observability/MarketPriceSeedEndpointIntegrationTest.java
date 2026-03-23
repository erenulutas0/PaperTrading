package com.finance.core.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.market.ws.enabled=false",
        "app.market.manual-seed-enabled=true"
})
@AutoConfigureMockMvc
class MarketPriceSeedEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSeedTrackedPriceAndExposeSnapshot() throws Exception {
        mockMvc.perform(post("/actuator/marketprices")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "BTCUSDT",
                                  "price": 50000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.seededSymbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.seededPrice").value(50000.0))
                .andExpect(jsonPath("$.prices.BTCUSDT").value(50000.0))
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/actuator/marketprices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.prices.BTCUSDT").value(50000.0))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.lastPriceUpdateAt").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidSeedRequests() throws Exception {
        mockMvc.perform(post("/actuator/marketprices")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "DOGEUSDT",
                                  "price": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.error").value("invalid_market_symbol"));

        mockMvc.perform(post("/actuator/marketprices")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "BTCUSDT",
                                  "price": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.error").value("invalid_market_price"));
    }
}
