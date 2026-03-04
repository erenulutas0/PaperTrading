package com.finance.core.controller;

import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.service.BinanceService;
import com.finance.core.service.UserPreferencesService;
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
                .build();

        when(userPreferencesService.getPreferences(any(UUID.class))).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard.dashboard.period").value("1M"))
                .andExpect(jsonPath("$.leaderboard.dashboard.sortBy").value("PROFIT_LOSS"))
                .andExpect(jsonPath("$.leaderboard.dashboard.direction").value("ASC"))
                .andExpect(jsonPath("$.leaderboard.publicPage.sortBy").value("RETURN_PERCENTAGE"))
                .andExpect(jsonPath("$.leaderboard.publicPage.direction").value("DESC"));
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
}

