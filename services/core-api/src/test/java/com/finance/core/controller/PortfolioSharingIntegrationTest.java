package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.PortfolioRequest;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioSharingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BinanceService binanceService;

    @BeforeEach
    void setUp() {
        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));
        portfolioRepository.deleteAll();
    }

    // ===== CREATE WITH VISIBILITY =====

    @Test
    void createPortfolio_defaultsToPrivate() throws Exception {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("My Private Portfolio");
        request.setOwnerId("user-1");

        String response = mockMvc.perform(post("/api/v1/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Private Portfolio"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andReturn().getResponse().getContentAsString();

        assertNotNull(response);
    }

    @Test
    void createPortfolio_withPublicVisibility() throws Exception {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("Public Portfolio");
        request.setOwnerId("user-1");
        request.setVisibility("PUBLIC");
        request.setDescription("My public trading strategy");

        mockMvc.perform(post("/api/v1/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.description").value("My public trading strategy"));
    }

    @Test
    void createPortfolio_invalidVisibility_defaultsToPrivate() throws Exception {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("Invalid Visibility");
        request.setOwnerId("user-1");
        request.setVisibility("INVALID_VALUE");

        mockMvc.perform(post("/api/v1/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE")); // Falls back to default
    }

    // ===== VISIBILITY TOGGLE =====

    @Test
    void toggleVisibility_privateToPUBLIC() throws Exception {
        // Create a private portfolio
        Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Toggle Test")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(10000))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());

        // Toggle to PUBLIC
        mockMvc.perform(put("/api/v1/portfolios/{id}/visibility", portfolio.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        // Verify in DB
        Portfolio updated = portfolioRepository.findById(portfolio.getId()).orElseThrow();
        assertEquals(Portfolio.Visibility.PUBLIC, updated.getVisibility());
    }

    @Test
    void toggleVisibility_publicToPrivate() throws Exception {
        Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Public Toggle")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(10000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        mockMvc.perform(put("/api/v1/portfolios/{id}/visibility", portfolio.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void toggleVisibility_invalidValue_returnsBadRequest() throws Exception {
        Portfolio portfolio = portfolioRepository.save(Portfolio.builder()
                .name("Invalid Toggle")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(10000))
                .build());

        mockMvc.perform(put("/api/v1/portfolios/{id}/visibility", portfolio.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"BANANA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void toggleVisibility_nonExistentPortfolio_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/portfolios/{id}/visibility", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isNotFound());
    }

    // ===== DISCOVER PUBLIC PORTFOLIOS =====

    @Test
    void discover_returnsOnlyPublicPortfolios() throws Exception {
        // Create 2 public, 1 private
        portfolioRepository.save(Portfolio.builder()
                .name("Public Alpha")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(50000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        portfolioRepository.save(Portfolio.builder()
                .name("Public Beta")
                .ownerId("user-2")
                .balance(BigDecimal.valueOf(25000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());

        portfolioRepository.save(Portfolio.builder()
                .name("Private Gamma")
                .ownerId("user-3")
                .balance(BigDecimal.valueOf(100000))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());

        mockMvc.perform(get("/api/v1/portfolios/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Public Alpha", "Public Beta")));
    }

    @Test
    void discover_returnsEmptyWhenNoPublicPortfolios() throws Exception {
        // Only create private portfolios
        portfolioRepository.save(Portfolio.builder()
                .name("Secret Portfolio")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(10000))
                .visibility(Portfolio.Visibility.PRIVATE)
                .build());

        mockMvc.perform(get("/api/v1/portfolios/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void discover_filtersByPortfolioName() throws Exception {
        portfolioRepository.save(Portfolio.builder()
                .name("Momentum Alpha")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(50000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .items(List.of())
                .build());

        portfolioRepository.save(Portfolio.builder()
                .name("Dividend Shield")
                .ownerId("user-2")
                .balance(BigDecimal.valueOf(25000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .items(List.of())
                .build());

        mockMvc.perform(get("/api/v1/portfolios/discover")
                        .param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Momentum Alpha"));
    }

    @Test
    void discover_filtersByPortfolioItemSymbol() throws Exception {
        Portfolio matching = Portfolio.builder()
                .name("Crypto Rotation")
                .ownerId("user-1")
                .balance(BigDecimal.valueOf(50000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build();
        matching.getItems().add(com.finance.core.domain.PortfolioItem.builder()
                .portfolio(matching)
                .symbol("BTCUSDT")
                .quantity(BigDecimal.ONE)
                .averagePrice(BigDecimal.valueOf(50000))
                .leverage(1)
                .side("LONG")
                .build());
        portfolioRepository.save(matching);

        Portfolio nonMatching = Portfolio.builder()
                .name("Equity Basket")
                .ownerId("user-2")
                .balance(BigDecimal.valueOf(25000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build();
        nonMatching.getItems().add(com.finance.core.domain.PortfolioItem.builder()
                .portfolio(nonMatching)
                .symbol("ETHUSDT")
                .quantity(BigDecimal.ONE)
                .averagePrice(BigDecimal.valueOf(3000))
                .leverage(1)
                .side("LONG")
                .build());
        portfolioRepository.save(nonMatching);

        mockMvc.perform(get("/api/v1/portfolios/discover")
                        .param("q", "btc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Crypto Rotation"));
    }

    // ===== FULL LIFECYCLE =====

    @Test
    void fullLifecycle_createPrivate_makePublic_discover_makePrivateAgain() throws Exception {
        // 1. Create private portfolio
        String createResponse = mockMvc.perform(post("/api/v1/portfolios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Lifecycle Test\", \"ownerId\": \"user-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andReturn().getResponse().getContentAsString();

        String portfolioId = objectMapper.readTree(createResponse).get("id").asText();

        // 2. Should NOT appear in discover
        mockMvc.perform(get("/api/v1/portfolios/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Lifecycle Test')]").doesNotExist());

        // 3. Make it public
        mockMvc.perform(put("/api/v1/portfolios/{id}/visibility", portfolioId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"PUBLIC\"}"))
                .andExpect(status().isOk());

        // 4. Should NOW appear in discover
        mockMvc.perform(get("/api/v1/portfolios/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Lifecycle Test')]").exists());

        // 5. Make it private again
        mockMvc.perform(put("/api/v1/portfolios/{id}/visibility", portfolioId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isOk());

        // 6. Should disappear from discover
        mockMvc.perform(get("/api/v1/portfolios/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Lifecycle Test')]").doesNotExist());
    }
}
