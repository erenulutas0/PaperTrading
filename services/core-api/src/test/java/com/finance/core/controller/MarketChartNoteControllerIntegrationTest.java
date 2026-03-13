package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.MarketChartNote;
import com.finance.core.dto.MarketType;
import com.finance.core.repository.MarketChartNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MarketChartNoteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketChartNoteRepository marketChartNoteRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        marketChartNoteRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    void createAndListNotes_shouldPersistAndReturnCurrentUsersSymbolNotes() throws Exception {
        Map<String, Object> request = Map.of(
                "market", "BIST100",
                "symbol", "AEFES",
                "body", "Delayed breakout watch");

        mockMvc.perform(post("/api/v1/market/chart-notes")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AEFES"))
                .andExpect(jsonPath("$.market").value("BIST100"))
                .andExpect(jsonPath("$.body").value("Delayed breakout watch"));

        mockMvc.perform(get("/api/v1/market/chart-notes")
                        .header("X-User-Id", userId.toString())
                        .param("market", "BIST100")
                        .param("symbol", "AEFES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("Delayed breakout watch"));
    }

    @Test
    void deleteNote_shouldDeleteOnlyCurrentUsersNote() throws Exception {
        MarketChartNote note = marketChartNoteRepository.save(MarketChartNote.builder()
                .userId(userId)
                .market(MarketType.CRYPTO)
                .symbol("BTCUSDT")
                .body("Keep above weekly support")
                .build());

        mockMvc.perform(delete("/api/v1/market/chart-notes/" + note.getId())
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/market/chart-notes")
                        .header("X-User-Id", userId.toString())
                        .param("market", "CRYPTO")
                        .param("symbol", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
