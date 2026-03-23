package com.finance.core.controller;

import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.service.BinanceService;
import com.finance.core.service.UserPreferencesService;
import com.finance.core.web.ApiRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserPreferencesControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserPreferencesService userPreferencesService;

    @MockitoBean
    private BinanceService binanceService;

    @Test
    void getPreferences_shouldReturnLeaderboardPayload() throws Exception {
        UserPreferencesResponse response = UserPreferencesResponse.builder()
                .leaderboard(UserPreferencesResponse.LeaderboardPreferences.builder()
                        .dashboard(UserPreferencesResponse.DashboardPreferences.builder()
                                .period("1M")
                                .sortBy("PROFIT_LOSS")
                                .direction("ASC")
                                .build())
                        .publicPage(UserPreferencesResponse.PublicPreferences.builder()
                                .sortBy("RETURN_PERCENTAGE")
                                .direction("DESC")
                                .build())
                        .build())
                .terminal(UserPreferencesResponse.TerminalPreferences.builder()
                        .market("CRYPTO")
                        .symbol("BTCUSDT")
                        .compareSymbols(java.util.List.of("ETHUSDT"))
                        .compareVisible(true)
                        .range("1D")
                        .interval("1h")
                        .favoriteSymbols(java.util.List.of("BTCUSDT"))
                        .compareBaskets(java.util.List.of(
                                UserPreferencesResponse.CompareBasket.builder()
                                        .name("Majors")
                                        .market("CRYPTO")
                                        .symbols(java.util.List.of("ETHUSDT", "BNBUSDT"))
                                        .updatedAt("2026-03-15T00:00:00Z")
                                        .build()))
                        .scannerViews(java.util.List.of(
                                UserPreferencesResponse.ScannerView.builder()
                                        .name("Crypto Movers")
                                        .market("CRYPTO")
                                        .quickFilter("GAINERS")
                                        .sortMode("MOVE_DESC")
                                        .query("btc")
                                        .anchorSymbol("BTCUSDT")
                                        .updatedAt("2026-03-15T00:00:00Z")
                                        .build()))
                        .build())
                .build();

        when(userPreferencesService.getPreferences(any(UUID.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard.dashboard.period").value("1M"))
                .andExpect(jsonPath("$.leaderboard.dashboard.sortBy").value("PROFIT_LOSS"))
                .andExpect(jsonPath("$.leaderboard.dashboard.direction").value("ASC"))
                .andExpect(jsonPath("$.leaderboard.publicPage.sortBy").value("RETURN_PERCENTAGE"))
                .andExpect(jsonPath("$.leaderboard.publicPage.direction").value("DESC"))
                .andExpect(jsonPath("$.terminal.market").value("CRYPTO"))
                .andExpect(jsonPath("$.terminal.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.terminal.compareBaskets[0].name").value("Majors"))
                .andExpect(jsonPath("$.terminal.scannerViews[0].name").value("Crypto Movers"));
    }

    @Test
    void getPreferences_withUnknownUser_shouldReturnExplicitNotFoundContract() throws Exception {
        when(userPreferencesService.getPreferences(any(UUID.class)))
                .thenThrow(ApiRequestException.notFound("user_not_found", "User not found"));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header("X-Request-Id", "prefs-missing-user-1")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("prefs-missing-user-1"));
    }

    @Test
    void updateLeaderboardPreferences_shouldReturnUpdatedPayload() throws Exception {
        UserPreferencesResponse response = UserPreferencesResponse.builder()
                .leaderboard(UserPreferencesResponse.LeaderboardPreferences.builder()
                        .dashboard(UserPreferencesResponse.DashboardPreferences.builder()
                                .period("ALL")
                                .sortBy("RETURN_PERCENTAGE")
                                .direction("DESC")
                                .build())
                        .publicPage(UserPreferencesResponse.PublicPreferences.builder()
                                .sortBy("PROFIT_LOSS")
                                .direction("ASC")
                                .build())
                        .build())
                .terminal(UserPreferencesResponse.TerminalPreferences.builder()
                        .market("CRYPTO")
                        .symbol("BTCUSDT")
                        .compareSymbols(java.util.List.of())
                        .compareVisible(true)
                        .range("1D")
                        .interval("1h")
                        .favoriteSymbols(java.util.List.of())
                        .compareBaskets(java.util.List.of())
                        .build())
                .build();
        when(userPreferencesService.updateLeaderboardPreferences(any(UUID.class), any(UpdateLeaderboardPreferencesRequest.class)))
                .thenReturn(response);

        String body = """
                {
                  "dashboard": {
                    "period": "ALL",
                    "sortBy": "RETURN_PERCENTAGE",
                    "direction": "DESC"
                  },
                  "publicPage": {
                    "sortBy": "PROFIT_LOSS",
                    "direction": "ASC"
                  }
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/preferences/leaderboard")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard.dashboard.period").value("ALL"))
                .andExpect(jsonPath("$.leaderboard.publicPage.sortBy").value("PROFIT_LOSS"))
                .andExpect(jsonPath("$.leaderboard.publicPage.direction").value("ASC"));
    }

    @Test
    void updateLeaderboardPreferences_withUnknownUser_shouldReturnExplicitNotFoundContract() throws Exception {
        when(userPreferencesService.updateLeaderboardPreferences(any(UUID.class), any(UpdateLeaderboardPreferencesRequest.class)))
                .thenThrow(ApiRequestException.notFound("user_not_found", "User not found"));

        mockMvc.perform(put("/api/v1/users/me/preferences/leaderboard")
                        .header("X-Request-Id", "prefs-leaderboard-missing-user-1")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboard\":{\"period\":\"1D\"}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("prefs-leaderboard-missing-user-1"));
    }

    @Test
    void updateLeaderboardPreferences_withInvalidPeriod_shouldReturnExplicitBadRequestContract() throws Exception {
        when(userPreferencesService.updateLeaderboardPreferences(any(UUID.class), any(UpdateLeaderboardPreferencesRequest.class)))
                .thenThrow(ApiRequestException.badRequest("invalid_user_preferences_period", "Invalid user preferences period"));

        mockMvc.perform(put("/api/v1/users/me/preferences/leaderboard")
                        .header("X-Request-Id", "prefs-leaderboard-invalid-period-1")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dashboard\":{\"period\":\"2Y\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "prefs-leaderboard-invalid-period-1"))
                .andExpect(jsonPath("$.code").value("invalid_user_preferences_period"))
                .andExpect(jsonPath("$.message").value("Invalid user preferences period"))
                .andExpect(jsonPath("$.requestId").value("prefs-leaderboard-invalid-period-1"));
    }

    @Test
    void updateTerminalPreferences_shouldReturnUpdatedTerminalPayload() throws Exception {
        UserPreferencesResponse response = UserPreferencesResponse.builder()
                .leaderboard(UserPreferencesResponse.LeaderboardPreferences.builder()
                        .dashboard(UserPreferencesResponse.DashboardPreferences.builder()
                                .period("1D")
                                .sortBy("RETURN_PERCENTAGE")
                                .direction("DESC")
                                .build())
                        .publicPage(UserPreferencesResponse.PublicPreferences.builder()
                                .sortBy("RETURN_PERCENTAGE")
                                .direction("DESC")
                                .build())
                        .build())
                .terminal(UserPreferencesResponse.TerminalPreferences.builder()
                        .market("BIST100")
                        .symbol("THYAO")
                        .compareSymbols(java.util.List.of("ISCTR", "GARAN"))
                        .compareVisible(false)
                        .range("6M")
                        .interval("4h")
                        .favoriteSymbols(java.util.List.of("THYAO"))
                        .compareBaskets(java.util.List.of(
                                UserPreferencesResponse.CompareBasket.builder()
                                        .name("Banks")
                                        .market("BIST100")
                                        .symbols(java.util.List.of("ISCTR", "GARAN"))
                                        .updatedAt("2026-03-15T00:00:00Z")
                                        .build()))
                        .scannerViews(java.util.List.of(
                                UserPreferencesResponse.ScannerView.builder()
                                        .name("Bank Winners")
                                        .market("BIST100")
                                        .quickFilter("GAINERS")
                                        .sortMode("ALPHA")
                                        .query("bank")
                                        .anchorSymbol("ISCTR")
                                        .updatedAt("2026-03-15T00:00:00Z")
                                        .build()))
                        .build())
                .build();
        when(userPreferencesService.updateTerminalPreferences(any(UUID.class), any(UpdateTerminalPreferencesRequest.class)))
                .thenReturn(response);

        String body = """
                {
                  "market": "BIST100",
                  "symbol": "THYAO",
                  "compareSymbols": ["ISCTR", "GARAN"],
                  "compareVisible": false,
                  "range": "6M",
                  "interval": "4h",
                  "favoriteSymbols": ["THYAO"],
                  "compareBaskets": [
                    {
                      "name": "Banks",
                      "market": "BIST100",
                      "symbols": ["ISCTR", "GARAN"],
                      "updatedAt": "2026-03-15T00:00:00Z"
                    }
                  ],
                  "scannerViews": [
                    {
                      "name": "Bank Winners",
                      "market": "BIST100",
                      "quickFilter": "GAINERS",
                      "sortMode": "ALPHA",
                      "query": "bank",
                      "anchorSymbol": "ISCTR",
                      "updatedAt": "2026-03-15T00:00:00Z"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/api/v1/users/me/preferences/terminal")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminal.market").value("BIST100"))
                .andExpect(jsonPath("$.terminal.symbol").value("THYAO"))
                .andExpect(jsonPath("$.terminal.compareSymbols[0]").value("ISCTR"))
                .andExpect(jsonPath("$.terminal.compareVisible").value(false))
                .andExpect(jsonPath("$.terminal.range").value("6M"))
                .andExpect(jsonPath("$.terminal.interval").value("4h"))
                .andExpect(jsonPath("$.terminal.compareBaskets[0].name").value("Banks"))
                .andExpect(jsonPath("$.terminal.scannerViews[0].name").value("Bank Winners"));
    }

    @Test
    void updateTerminalPreferences_withUnknownUser_shouldReturnExplicitNotFoundContract() throws Exception {
        when(userPreferencesService.updateTerminalPreferences(any(UUID.class), any(UpdateTerminalPreferencesRequest.class)))
                .thenThrow(ApiRequestException.notFound("user_not_found", "User not found"));

        mockMvc.perform(put("/api/v1/users/me/preferences/terminal")
                        .header("X-Request-Id", "prefs-terminal-missing-user-1")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"market\":\"CRYPTO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("prefs-terminal-missing-user-1"));
    }

    @Test
    void updateTerminalPreferences_withInvalidScannerSort_shouldReturnExplicitBadRequestContract() throws Exception {
        when(userPreferencesService.updateTerminalPreferences(any(UUID.class), any(UpdateTerminalPreferencesRequest.class)))
                .thenThrow(ApiRequestException.badRequest("invalid_user_preferences_scanner_sort", "Invalid user preferences scanner sort"));

        mockMvc.perform(put("/api/v1/users/me/preferences/terminal")
                        .header("X-Request-Id", "prefs-terminal-invalid-scanner-sort-1")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scannerViews\":[{\"name\":\"Bad\",\"market\":\"CRYPTO\",\"quickFilter\":\"ALL\",\"sortMode\":\"BROKEN\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "prefs-terminal-invalid-scanner-sort-1"))
                .andExpect(jsonPath("$.code").value("invalid_user_preferences_scanner_sort"))
                .andExpect(jsonPath("$.message").value("Invalid user preferences scanner sort"))
                .andExpect(jsonPath("$.requestId").value("prefs-terminal-invalid-scanner-sort-1"));
    }

    @Test
    void updateTerminalPreferences_withCompareBasketLimit_shouldReturnExplicitConflictContract() throws Exception {
        when(userPreferencesService.updateTerminalPreferences(any(UUID.class), any(UpdateTerminalPreferencesRequest.class)))
                .thenThrow(ApiRequestException.conflict(
                        "user_preferences_compare_basket_limit_reached",
                        "Compare basket limit reached"));

        mockMvc.perform(put("/api/v1/users/me/preferences/terminal")
                        .header("X-Request-Id", "prefs-terminal-basket-limit-1")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compareBaskets\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "prefs-terminal-basket-limit-1"))
                .andExpect(jsonPath("$.code").value("user_preferences_compare_basket_limit_reached"))
                .andExpect(jsonPath("$.message").value("Compare basket limit reached"))
                .andExpect(jsonPath("$.requestId").value("prefs-terminal-basket-limit-1"));
    }
}
