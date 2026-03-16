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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                .andExpect(jsonPath("$.body").value("Delayed breakout watch"))
                .andExpect(jsonPath("$.pinned").value(false));

        mockMvc.perform(get("/api/v1/market/chart-notes")
                        .header("X-User-Id", userId.toString())
                        .param("market", "BIST100")
                        .param("symbol", "AEFES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].body").value("Delayed breakout watch"))
                .andExpect(jsonPath("$.content[0].pinned").value(false));
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
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void updateNote_shouldUpdateBodyForCurrentUser() throws Exception {
        MarketChartNote note = marketChartNoteRepository.save(MarketChartNote.builder()
                .userId(userId)
                .market(MarketType.CRYPTO)
                .symbol("ETHUSDT")
                .body("Old note")
                .build());

        Map<String, Object> request = Map.of(
                "market", "CRYPTO",
                "symbol", "ETHUSDT",
                "body", "Updated note",
                "pinned", true);

        mockMvc.perform(put("/api/v1/market/chart-notes/" + note.getId())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Updated note"))
                .andExpect(jsonPath("$.pinned").value(true));

        mockMvc.perform(get("/api/v1/market/chart-notes")
                        .header("X-User-Id", userId.toString())
                        .param("market", "CRYPTO")
                        .param("symbol", "ETHUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("Updated note"))
                .andExpect(jsonPath("$.content[0].pinned").value(true));
    }

    @Test
    void listNotes_shouldReturnPinnedNotesFirst() throws Exception {
        marketChartNoteRepository.save(MarketChartNote.builder()
                .userId(userId)
                .market(MarketType.CRYPTO)
                .symbol("BTCUSDT")
                .body("Older pinned note")
                .pinned(true)
                .build());

        marketChartNoteRepository.save(MarketChartNote.builder()
                .userId(userId)
                .market(MarketType.CRYPTO)
                .symbol("BTCUSDT")
                .body("Latest unpinned note")
                .pinned(false)
                .build());

        mockMvc.perform(get("/api/v1/market/chart-notes")
                        .header("X-User-Id", userId.toString())
                        .param("market", "CRYPTO")
                        .param("symbol", "BTCUSDT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].body").value("Older pinned note"))
                .andExpect(jsonPath("$.content[0].pinned").value(true))
                .andExpect(jsonPath("$.content[1].body").value("Latest unpinned note"))
                .andExpect(jsonPath("$.content[1].pinned").value(false));
    }
}
