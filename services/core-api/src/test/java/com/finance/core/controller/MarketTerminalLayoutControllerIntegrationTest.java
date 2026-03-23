package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.MarketTerminalLayout;
import com.finance.core.repository.MarketTerminalLayoutRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.MarketTerminalLayoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    @SpyBean
    private MarketTerminalLayoutService marketTerminalLayoutService;

    private UUID userId;
    private UUID otherUserId;

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
        AppUser otherUser = userRepository.save(AppUser.builder()
                .username("layout-user-other")
                .email("layout-user-other@test.com")
                .password("plaintext")
                .build());
        otherUserId = otherUser.getId();
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
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("BIST Swing"))
                .andExpect(jsonPath("$.content[0].compareSymbols[0]").value("GARAN"))
                .andExpect(jsonPath("$.page.totalElements").value(1));

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
                .andExpect(jsonPath("$.content", hasSize(0)));
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
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Newer Layout"));
    }

    @Test
    void listLayouts_withInvalidSize_shouldReturnExplicitBadRequestContract() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString())
                        .header("X-Request-Id", "layout-page-err-1")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "layout-page-err-1"))
                .andExpect(jsonPath("$.code").value("invalid_market_terminal_layout_size"))
                .andExpect(jsonPath("$.message").value("Invalid market terminal layout size"))
                .andExpect(jsonPath("$.requestId").value("layout-page-err-1"));
    }

    @Test
    void createLayout_whenLimitReached_shouldReturnExplicitConflictContract() throws Exception {
        for (int index = 0; index < 10; index++) {
            marketTerminalLayoutRepository.save(MarketTerminalLayout.builder()
                    .userId(userId)
                    .name("Layout " + index)
                    .market("CRYPTO")
                    .symbol("BTCUSDT")
                    .range("1D")
                    .interval("1h")
                    .build());
        }

        mockMvc.perform(post("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Overflow Layout"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("market_terminal_layout_limit_reached"));
    }

    @Test
    void updateLayout_nonOwnedLayout_shouldReturnExplicitNotFoundContract() throws Exception {
        MarketTerminalLayout layout = marketTerminalLayoutRepository.save(MarketTerminalLayout.builder()
                .userId(otherUserId)
                .name("Other Layout")
                .market("CRYPTO")
                .symbol("BTCUSDT")
                .range("1D")
                .interval("1h")
                .build());

        mockMvc.perform(put("/api/v1/users/me/preferences/terminal-layouts/" + layout.getId())
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Hijack Attempt"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("market_terminal_layout_not_found"));
    }

    @Test
    void createLayout_withBlankName_shouldReturnExplicitValidationContract() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/preferences/terminal-layouts")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("market_terminal_layout_name_required"));
    }

    @Test
    void deleteLayout_whenUnexpectedRuntime_shouldReturnDeleteFallbackContract() throws Exception {
        UUID layoutId = UUID.randomUUID();
        doThrow(new RuntimeException("layout storage exploded"))
                .when(marketTerminalLayoutService)
                .deleteLayout(userId, layoutId);

        mockMvc.perform(delete("/api/v1/users/me/preferences/terminal-layouts/" + layoutId)
                        .header("X-User-Id", userId.toString())
                        .header("X-Request-Id", "layout-delete-fallback-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("market_terminal_layout_delete_failed"))
                .andExpect(jsonPath("$.message").value("Failed to delete market terminal layout"))
                .andExpect(jsonPath("$.requestId").value("layout-delete-fallback-1"));
    }
}
