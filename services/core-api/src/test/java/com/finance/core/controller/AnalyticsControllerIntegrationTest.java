package com.finance.core.controller;

import com.finance.core.domain.Portfolio;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.PortfolioSnapshotRepository;
import com.finance.core.repository.TradeActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;
    @Autowired
    private TradeActivityRepository tradeActivityRepository;

    private Portfolio testPortfolio;
    private UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        snapshotRepository.deleteAll();
        tradeActivityRepository.deleteAll();
        portfolioRepository.deleteAll();

        testPortfolio = Portfolio.builder()
                .name("Analytics Test")
                .ownerId(userId.toString())
                .balance(new BigDecimal("100000"))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build();
        testPortfolio = portfolioRepository.save(testPortfolio);
    }

    @Test
    void testGetFullAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/" + testPortfolio.getId())
                .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.summary.portfolioName").value("Analytics Test"))
                .andExpect(jsonPath("$.summary.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.summary.contributionSummary").exists())
                .andExpect(jsonPath("$.summary.highlightSummary").exists())
                .andExpect(jsonPath("$.positionSummary").exists())
                .andExpect(jsonPath("$.positionSummary.openPositions").value(0))
                .andExpect(jsonPath("$.riskAttribution").isArray())
                .andExpect(jsonPath("$.performanceWindows").exists())
                .andExpect(jsonPath("$.performanceWindows.7d").exists())
                .andExpect(jsonPath("$.periodExtremes").exists())
                .andExpect(jsonPath("$.periodExtremes.bestMove").exists())
                .andExpect(jsonPath("$.symbolAttribution").isArray())
                .andExpect(jsonPath("$.symbolMiniTimelines").isArray())
                .andExpect(jsonPath("$.pnlTimeline").isArray())
                .andExpect(jsonPath("$.riskMetrics").exists())
                .andExpect(jsonPath("$.tradeStats").exists())
                .andExpect(jsonPath("$.equityCurve").exists());
    }

    @Test
    void testExportAnalyticsCsv() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/" + testPortfolio.getId() + "/export")
                .header("X-User-Id", userId.toString())
                .param("format", "csv")
                .param("curveWindow", "7D"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andExpect(content().contentType("text/csv"));
    }

    @Test
    void testGetRiskMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/" + testPortfolio.getId() + "/risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDrawdown").value(0.0))
                .andExpect(jsonPath("$.sharpeRatio").value(0.0))
                .andExpect(jsonPath("$.profitFactor").value(0.0));
    }
}
