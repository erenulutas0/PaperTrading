package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.MarketTerminalLayout;
import com.finance.core.repository.MarketTerminalLayoutRepository;
import com.finance.core.repository.UserRepository;
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
class MarketTerminalLayoutControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MarketTerminalLayoutRepository marketTerminalLayoutRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        marketTerminalLayoutRepository.deleteAll();
        userRepository.deleteAll();
        AppUser user = userRepository.save(AppUser.builder()
                .username("layout-user")
                .email("layout-user@test.com")
                .password("plaintext")
                .build());
        userId = user.getId();
    }

    @Test
    void createListUpdateAndDeleteLayouts_shouldWorkForCurrentUser() throws Exception {
        Map<String, Object> createRequest = Map.of(
                "name", "BIST Swing",
                "market", "BIST100",
                "symbol", "THYAO",
                "compareSymbols", java.util.List.of("GARAN", "ISCTR"),
                "compareVisible", true,
                "range", "6M",
                "interval", "4h",
                "favoriteSymbols", java.util.List.of("THYAO", "GARAN"));

        String responseBody = mockMvc.perform(post("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BIST Swing"))
                .andExpect(jsonPath("$.market").value("BIST100"))
                .andExpect(jsonPath("$.symbol").value("THYAO"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID layoutId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

        mockMvc.perform(get("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("BIST Swing"))
                .andExpect(jsonPath("$[0].compareSymbols[0]").value("GARAN"));

        Map<String, Object> updateRequest = Map.of(
                "name", "Crypto Momentum",
                "market", "CRYPTO",
                "symbol", "BTCUSDT",
                "compareSymbols", java.util.List.of("ETHUSDT"),
                "compareVisible", false,
                "range", "1M",
                "interval", "1h",
                "favoriteSymbols", java.util.List.of("BTCUSDT"));

        mockMvc.perform(put("/api/v1/users/me/preferences/terminal-layouts/" + layoutId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Crypto Momentum"))
                .andExpect(jsonPath("$.market").value("CRYPTO"))
                .andExpect(jsonPath("$.compareVisible").value(false));

        mockMvc.perform(delete("/api/v1/users/me/preferences/terminal-layouts/" + layoutId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listLayouts_shouldReturnNewestUpdatedFirst() throws Exception {
        marketTerminalLayoutRepository.save(MarketTerminalLayout.builder()
                .userId(userId)
                .name("Older Layout")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .range("1D")
                .interval("1h")
                .build());

        marketTerminalLayoutRepository.save(MarketTerminalLayout.builder()
                .userId(userId)
                .name("Newer Layout")
                .market("BIST100")
                .symbol("THYAO")
                .range("1W")
                .interval("4h")
                .build());

        mockMvc.perform(get("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Newer Layout"));
    }
}
