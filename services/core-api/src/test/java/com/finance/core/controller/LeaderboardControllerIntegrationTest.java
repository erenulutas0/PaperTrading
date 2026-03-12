package com.finance.core.controller;

import com.finance.core.dto.AccountLeaderboardEntry;
import com.finance.core.dto.LeaderboardEntry;
import com.finance.core.service.BinanceService;
import com.finance.core.service.LeaderboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LeaderboardControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private LeaderboardService leaderboardService;

        @MockitoBean
        private BinanceService binanceService;

        @BeforeEach
        void setUp() {
                // No DB setup needed as we mock the service
        }

        @Test
        void getLeaderboard_returnsPaginatedResults() throws Exception {
                LeaderboardEntry entry1 = LeaderboardEntry.builder()
                                .rank(1)
                                .portfolioName("Alpha")
                                .returnPercentage(BigDecimal.valueOf(20.0))
                                .build();

                LeaderboardEntry entry2 = LeaderboardEntry.builder()
                                .rank(2)
                                .portfolioName("Beta")
                                .returnPercentage(BigDecimal.valueOf(-10.0))
                                .build();

                Page<LeaderboardEntry> page = new PageImpl<>(List.of(entry1, entry2), PageRequest.of(0, 10), 2);

                when(leaderboardService.getLeaderboard(eq("1D"), eq("PROFIT_LOSS"), eq("ASC"), any(Pageable.class)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/v1/leaderboards?period=1D&sortBy=PROFIT_LOSS&direction=ASC&page=0&size=10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(2)))
                                .andExpect(jsonPath("$.page.totalElements").value(2))
                                .andExpect(jsonPath("$.content[0].portfolioName").value("Alpha"))
                                .andExpect(jsonPath("$.content[0].returnPercentage").value(20.0))
                                .andExpect(jsonPath("$.content[1].portfolioName").value("Beta"))
                                .andExpect(jsonPath("$.content[1].returnPercentage").value(-10.0));
        }

        @Test
        void getLeaderboard_emptyIfNoData() throws Exception {
                when(leaderboardService.getLeaderboard(eq("1D"), eq("RETURN_PERCENTAGE"), eq("DESC"),
                                any(Pageable.class)))
                                .thenReturn(Page.empty());

                mockMvc.perform(get("/api/v1/leaderboards?period=1D"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(0)))
                                .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        void getAccountLeaderboard_returnsPaginatedResults() throws Exception {
                AccountLeaderboardEntry entry = AccountLeaderboardEntry.builder()
                                .rank(1)
                                .ownerId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                                .ownerName("Trader X")
                                .publicPortfolioCount(3)
                                .trustScore(63.4)
                                .winRate(68.5)
                                .returnPercentage(BigDecimal.valueOf(12.3))
                                .profitLoss(BigDecimal.valueOf(4200))
                                .totalEquity(BigDecimal.valueOf(145000))
                                .startEquity(BigDecimal.valueOf(129000))
                                .build();

                Page<AccountLeaderboardEntry> page = new PageImpl<>(List.of(entry), PageRequest.of(0, 10), 1);

                when(leaderboardService.getAccountLeaderboard(eq("1W"), eq("TRUST_SCORE"), eq("DESC"), any(Pageable.class)))
                                .thenReturn(page);

                mockMvc.perform(get("/api/v1/leaderboards/accounts?period=1W&sortBy=TRUST_SCORE&direction=DESC&page=0&size=10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].ownerName").value("Trader X"))
                                .andExpect(jsonPath("$.content[0].publicPortfolioCount").value(3))
                                .andExpect(jsonPath("$.content[0].trustScore").value(63.4))
                                .andExpect(jsonPath("$.content[0].winRate").value(68.5));
        }
}
