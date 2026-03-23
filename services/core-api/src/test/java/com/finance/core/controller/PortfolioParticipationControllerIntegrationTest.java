package com.finance.core.controller;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Portfolio;
import com.finance.core.domain.PortfolioParticipant;
import com.finance.core.repository.PortfolioParticipantRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.StrategyBotRepository;
import com.finance.core.repository.StrategyBotRunEquityPointRepository;
import com.finance.core.repository.StrategyBotRunFillRepository;
import com.finance.core.repository.StrategyBotRunRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioParticipationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioParticipantRepository portfolioParticipantRepository;

    @Autowired
    private com.finance.core.repository.ActivityEventRepository activityEventRepository;

    @Autowired
    private com.finance.core.repository.NotificationRepository notificationRepository;

    @Autowired
    private StrategyBotRepository strategyBotRepository;

    @Autowired
    private StrategyBotRunRepository strategyBotRunRepository;

    @Autowired
    private StrategyBotRunFillRepository strategyBotRunFillRepository;

    @Autowired
    private StrategyBotRunEquityPointRepository strategyBotRunEquityPointRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private BinanceService binanceService;

    private AppUser owner;
    private AppUser participant;
    private Portfolio publicPortfolio;

    @BeforeEach
    void setUp() {
        when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 50000.0));

        notificationRepository.deleteAll();
        activityEventRepository.deleteAll();
        portfolioParticipantRepository.deleteAll();
        strategyBotRunEquityPointRepository.deleteAll();
        strategyBotRunFillRepository.deleteAll();
        strategyBotRunRepository.deleteAll();
        strategyBotRepository.deleteAll();
        portfolioRepository.deleteAll();
        userRepository.deleteAll();

        owner = userRepository.save(AppUser.builder()
                .username("owner_" + UUID.randomUUID().toString().substring(0, 5))
                .email("owner_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com")
                .password("password123")
                .build());

        participant = userRepository.save(AppUser.builder()
                .username("participant_" + UUID.randomUUID().toString().substring(0, 5))
                .email("participant_" + UUID.randomUUID().toString().substring(0, 5) + "@test.com")
                .password("password123")
                .build());

        publicPortfolio = portfolioRepository.save(Portfolio.builder()
                .name("Public Joinable")
                .ownerId(owner.getId().toString())
                .balance(BigDecimal.valueOf(10000))
                .visibility(Portfolio.Visibility.PUBLIC)
                .build());
    }

    @Test
    void joinPortfolio_missingPortfolio_returnsUnifiedNotFoundContract() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/join", UUID.randomUUID())
                        .header("X-User-Id", participant.getId().toString())
                        .header("X-Request-Id", "participation-err-1"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "participation-err-1"))
                .andExpect(jsonPath("$.code").value("portfolio_not_found"))
                .andExpect(jsonPath("$.message").value("Portfolio not found"))
                .andExpect(jsonPath("$.requestId").value("participation-err-1"));
    }

    @Test
    void joinPortfolio_duplicateJoin_returnsUnifiedConflictContract() throws Exception {
        portfolioParticipantRepository.save(PortfolioParticipant.builder()
                .portfolioId(publicPortfolio.getId())
                .userId(participant.getId())
                .clonedPortfolioId(UUID.randomUUID())
                .build());

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/join", publicPortfolio.getId())
                        .header("X-User-Id", participant.getId().toString())
                        .header("X-Request-Id", "participation-err-2"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "participation-err-2"))
                .andExpect(jsonPath("$.code").value("portfolio_already_joined"))
                .andExpect(jsonPath("$.message").value("Already joined this portfolio"))
                .andExpect(jsonPath("$.requestId").value("participation-err-2"));
    }

    @Test
    void leavePortfolio_whenNotParticipant_returnsUnifiedNotFoundContract() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolios/{portfolioId}/leave", publicPortfolio.getId())
                        .header("X-User-Id", participant.getId().toString())
                        .header("X-Request-Id", "participation-err-3"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "participation-err-3"))
                .andExpect(jsonPath("$.code").value("portfolio_participation_not_found"))
                .andExpect(jsonPath("$.message").value("Not a participant of this portfolio"))
                .andExpect(jsonPath("$.requestId").value("participation-err-3"));
    }

    @Test
    void joinPortfolio_missingUser_returnsUnifiedNotFoundContract() throws Exception {
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/join", publicPortfolio.getId())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Request-Id", "participation-err-4"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "participation-err-4"))
                .andExpect(jsonPath("$.code").value("user_not_found"))
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.requestId").value("participation-err-4"));
    }
}
