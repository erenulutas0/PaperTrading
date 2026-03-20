package com.finance.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Badge;
import com.finance.core.domain.Tournament;
import com.finance.core.repository.*;
import com.finance.core.service.BinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TournamentControllerIntegrationTest {

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private TournamentRepository tournamentRepository;
        @Autowired
        private TournamentParticipantRepository participantRepository;
        @Autowired
        private PortfolioRepository portfolioRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private BadgeRepository badgeRepository;
        @Autowired
        private ActivityEventRepository activityEventRepository;
        @Autowired
        private NotificationRepository notificationRepository;
        @Autowired
        private InteractionRepository interactionRepository;
        @Autowired
        private ObjectMapper objectMapper;

        @org.springframework.test.context.bean.override.mockito.MockitoBean
        private BinanceService binanceService;

        private AppUser testUser;

        @BeforeEach
        void setUp() {
                when(binanceService.getPrices()).thenReturn(java.util.Map.of("BTCUSDT", 50000.0));
                interactionRepository.deleteAll();
                notificationRepository.deleteAll();
                activityEventRepository.deleteAll();
                badgeRepository.deleteAll();
                participantRepository.deleteAll();
                portfolioRepository.deleteAll();
                tournamentRepository.deleteAll();
                userRepository.deleteAll();

                testUser = AppUser.builder()
                                .username("tourney_tester")
                                .email("tester@tourney.com")
                                .password("password")
                                .build();
                testUser = userRepository.save(testUser);
        }

        @Test
        void testCreateAndListTournaments() throws Exception {
                // 1. Create tournament
                Tournament tournament = Tournament.builder()
                                .name("The Grand Cup")
                                .description("Test Description")
                                .startingBalance(new BigDecimal("100000"))
                                .startsAt(LocalDateTime.now().plusDays(1))
                                .endsAt(LocalDateTime.now().plusDays(8))
                                .status(Tournament.Status.UPCOMING)
                                .build();

                mockMvc.perform(post("/api/v1/tournaments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(tournament)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("The Grand Cup"))
                                .andExpect(jsonPath("$.status").value("UPCOMING"));

                // 2. List tournaments
                mockMvc.perform(get("/api/v1/tournaments"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                                .andExpect(jsonPath("$.content[0].name").value("The Grand Cup"));
        }

        @Test
        void testJoinTournament() throws Exception {
                // 1. Arrange: Create an active tournament
                Tournament active = Tournament.builder()
                                .name("Active Challenge")
                                .startingBalance(new BigDecimal("50000"))
                                .status(Tournament.Status.ACTIVE)
                                .startsAt(LocalDateTime.now().minusDays(1))
                                .endsAt(LocalDateTime.now().plusDays(1))
                                .build();
                active = tournamentRepository.save(active);

                // 2. Act: Join
                mockMvc.perform(post("/api/v1/tournaments/" + active.getId() + "/join")
                                .header("X-User-Id", testUser.getId().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.portfolioId").exists())
                                .andExpect(jsonPath("$.tournamentName").value("Active Challenge"));

                // 3. Assert: Verify side effects
                assert participantRepository.existsByTournamentIdAndUserId(active.getId(), testUser.getId());

                // Verify a new portfolio was created with the tournament's starting balance
                UUID portfolioId = UUID.fromString(
                                participantRepository.findByTournamentId(active.getId()).get(0).getPortfolioId()
                                                .toString());
                assert portfolioRepository.findById(portfolioId)
                                .map(p -> p.getBalance().compareTo(new BigDecimal("50000")) == 0)
                                .orElse(false);
        }

        @Test
        void testGetLeaderboard() throws Exception {
                Tournament t = Tournament.builder()
                                .name("Leaderboard Test")
                                .status(Tournament.Status.ACTIVE)
                                .startsAt(LocalDateTime.now().minusDays(1))
                                .endsAt(LocalDateTime.now().plusDays(1))
                                .build();
                t = tournamentRepository.save(t);

                mockMvc.perform(get("/api/v1/tournaments/" + t.getId() + "/leaderboard"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(0)))
                                .andExpect(jsonPath("$.page.totalElements").value(0));
        }

        @Test
        void testGetUserBadgesPaged() throws Exception {
                badgeRepository.save(Badge.builder()
                                .userId(testUser.getId())
                                .name("First Tournament")
                                .icon("🏁")
                                .description("Joined your first paper trading tournament!")
                                .build());

                mockMvc.perform(get("/api/v1/tournaments/badges/" + testUser.getId())
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content", hasSize(1)))
                                .andExpect(jsonPath("$.content[0].name").value("First Tournament"))
                                .andExpect(jsonPath("$.page.totalElements").value(1));
        }

        @Test
        void testJoinTournament_missingTournament_shouldReturnUnifiedErrorContract() throws Exception {
                mockMvc.perform(post("/api/v1/tournaments/" + UUID.randomUUID() + "/join")
                                .header("X-User-Id", testUser.getId().toString())
                                .header("X-Request-Id", "tournament-err-1"))
                                .andExpect(status().isNotFound())
                                .andExpect(header().string("X-Request-Id", "tournament-err-1"))
                                .andExpect(jsonPath("$.code").value("tournament_not_found"))
                                .andExpect(jsonPath("$.message").value("Tournament not found"))
                                .andExpect(jsonPath("$.requestId").value("tournament-err-1"));
        }

        @Test
        void testJoinTournament_duplicateJoin_shouldReturnUnifiedErrorContract() throws Exception {
                Tournament active = Tournament.builder()
                                .name("Duplicate Join Challenge")
                                .startingBalance(new BigDecimal("50000"))
                                .status(Tournament.Status.ACTIVE)
                                .startsAt(LocalDateTime.now().minusDays(1))
                                .endsAt(LocalDateTime.now().plusDays(1))
                                .build();
                active = tournamentRepository.save(active);

                mockMvc.perform(post("/api/v1/tournaments/" + active.getId() + "/join")
                                .header("X-User-Id", testUser.getId().toString()))
                                .andExpect(status().isOk());

                mockMvc.perform(post("/api/v1/tournaments/" + active.getId() + "/join")
                                .header("X-User-Id", testUser.getId().toString())
                                .header("X-Request-Id", "tournament-err-2"))
                                .andExpect(status().isConflict())
                                .andExpect(header().string("X-Request-Id", "tournament-err-2"))
                                .andExpect(jsonPath("$.code").value("tournament_already_joined"))
                                .andExpect(jsonPath("$.message").value("Already joined this tournament"))
                                .andExpect(jsonPath("$.requestId").value("tournament-err-2"));
        }
}
