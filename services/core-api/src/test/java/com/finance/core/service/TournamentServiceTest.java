package com.finance.core.service;

import com.finance.core.domain.*;
import com.finance.core.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;
    @Mock
    private TournamentParticipantRepository participantRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private BadgeRepository badgeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BinanceService binanceService;

    @InjectMocks
    private TournamentService tournamentService;

    private Tournament activeTournament;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        activeTournament = Tournament.builder()
                .id(UUID.randomUUID())
                .name("Weekly Challenge")
                .description("Test tournament")
                .startingBalance(new BigDecimal("100000"))
                .status(Tournament.Status.ACTIVE)
                .startsAt(LocalDateTime.now().minusHours(1))
                .endsAt(LocalDateTime.now().plusDays(7))
                .build();

        testUser = AppUser.builder()
                .id(UUID.randomUUID())
                .username("trader1")
                .email("trader1@test.com")
                .password("pass")
                .build();
    }

    @Nested
    class CreateTournament {

        @Test
        void createsWithDefaultBalance() {
            when(tournamentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Tournament t = tournamentService.createTournament(
                    "Test Cup", "A test",
                    null, // null balance = defaults to 100k
                    LocalDateTime.now().plusHours(1),
                    LocalDateTime.now().plusDays(7));

            assertNotNull(t);
            assertEquals("Test Cup", t.getName());
            assertEquals(0, new BigDecimal("100000").compareTo(t.getStartingBalance()));
            assertEquals(Tournament.Status.UPCOMING, t.getStatus());
        }

        @Test
        void createsAsActiveIfStartIsInPast() {
            when(tournamentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Tournament t = tournamentService.createTournament(
                    "Already Started", null,
                    new BigDecimal("50000"),
                    LocalDateTime.now().minusMinutes(5),
                    LocalDateTime.now().plusDays(1));

            assertEquals(Tournament.Status.ACTIVE, t.getStatus());
            assertEquals(0, new BigDecimal("50000").compareTo(t.getStartingBalance()));
        }
    }

    @Nested
    class JoinTournament {

        @Test
        void successfulJoin_createsPortfolioAndParticipant() {
            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));
            when(participantRepository.existsByTournamentIdAndUserId(any(), any())).thenReturn(false);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(portfolioRepository.save(any())).thenAnswer(inv -> {
                Portfolio p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(participantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
            when(participantRepository.findByUserId(any())).thenReturn(List.of(
                    TournamentParticipant.builder().build())); // first tournament

            Map<String, Object> result = tournamentService.joinTournament(activeTournament.getId(), testUser.getId());

            assertNotNull(result.get("portfolioId"));
            assertEquals("Weekly Challenge", result.get("tournamentName"));

            // Should create the portfolio with correct balance
            verify(portfolioRepository).save(argThat(p -> p.getBalance().compareTo(new BigDecimal("100000")) == 0 &&
                    p.getVisibility() == Portfolio.Visibility.PUBLIC));

            // Should save participant
            verify(participantRepository).saveAndFlush(any(TournamentParticipant.class));

            // Should award "First Tournament" badge
            verify(badgeRepository).save(argThat(b -> b.getName().equals("First Tournament")));
        }

        @Test
        void alreadyJoined_throwsException() {
            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));
            when(participantRepository.existsByTournamentIdAndUserId(activeTournament.getId(), testUser.getId()))
                    .thenReturn(true);

            assertThrows(RuntimeException.class,
                    () -> tournamentService.joinTournament(activeTournament.getId(), testUser.getId()));
        }

        @Test
        void duplicateJoinConstraintRace_normalizesConflictBeforeBadgeSideEffects() {
            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));
            when(participantRepository.existsByTournamentIdAndUserId(activeTournament.getId(), testUser.getId()))
                    .thenReturn(false);
            when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
            when(portfolioRepository.save(any())).thenAnswer(inv -> {
                Portfolio p = inv.getArgument(0);
                p.setId(UUID.randomUUID());
                return p;
            });
            when(participantRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tournamentService.joinTournament(activeTournament.getId(), testUser.getId()));

            assertEquals("Already joined this tournament", ex.getMessage());
            verify(badgeRepository, never()).save(any());
            verify(participantRepository, never()).findByUserId(any());
        }

        @Test
        void upcomingTournament_throwsException() {
            activeTournament.setStatus(Tournament.Status.UPCOMING);
            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));

            assertThrows(RuntimeException.class,
                    () -> tournamentService.joinTournament(activeTournament.getId(), testUser.getId()));
        }

        @Test
        void completedTournament_throwsException() {
            activeTournament.setStatus(Tournament.Status.COMPLETED);
            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));

            assertThrows(RuntimeException.class,
                    () -> tournamentService.joinTournament(activeTournament.getId(), testUser.getId()));
        }
    }

    @Nested
    class Leaderboard {

        @Test
        void emptyLeaderboard_whenNoParticipants() {
            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));
            when(participantRepository.findByTournamentId(activeTournament.getId())).thenReturn(List.of());

            List<Map<String, Object>> leaderboard = tournamentService
                    .getTournamentLeaderboard(activeTournament.getId());

            assertTrue(leaderboard.isEmpty());
        }

        @Test
        void liveLeaderboard_ranksParticipantsByReturn() {
            UUID portfolioAId = UUID.randomUUID();
            UUID portfolioBId = UUID.randomUUID();
            UUID userAId = UUID.randomUUID();
            UUID userBId = UUID.randomUUID();

            TournamentParticipant pA = TournamentParticipant.builder()
                    .userId(userAId).portfolioId(portfolioAId).tournamentId(activeTournament.getId()).build();
            TournamentParticipant pB = TournamentParticipant.builder()
                    .userId(userBId).portfolioId(portfolioBId).tournamentId(activeTournament.getId()).build();

            // Portfolio A: has a BTC position that's up
            PortfolioItem itemA = PortfolioItem.builder()
                    .symbol("BTCUSDT").quantity(BigDecimal.ONE).averagePrice(new BigDecimal("50000"))
                    .leverage(1).side("LONG").build();
            Portfolio portfolioA = Portfolio.builder()
                    .id(portfolioAId).balance(new BigDecimal("50000")).items(List.of(itemA)).build();

            // Portfolio B: no positions, just cash
            Portfolio portfolioB = Portfolio.builder()
                    .id(portfolioBId).balance(new BigDecimal("100000")).items(List.of()).build();

            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));
            when(participantRepository.findByTournamentId(activeTournament.getId())).thenReturn(List.of(pA, pB));
            when(portfolioRepository.findById(portfolioAId)).thenReturn(Optional.of(portfolioA));
            when(portfolioRepository.findById(portfolioBId)).thenReturn(Optional.of(portfolioB));
            when(binanceService.getPrices()).thenReturn(Map.of("BTCUSDT", 55000.0)); // BTC went up
            when(userRepository.findById(userAId)).thenReturn(Optional.of(
                    AppUser.builder().id(userAId).username("traderA").build()));
            when(userRepository.findById(userBId)).thenReturn(Optional.of(
                    AppUser.builder().id(userBId).username("traderB").build()));

            List<Map<String, Object>> leaderboard = tournamentService
                    .getTournamentLeaderboard(activeTournament.getId());

            assertEquals(2, leaderboard.size());

            // TraderA should be #1 (equity = 50000 balance + 50000 margin + 5000 PnL =
            // 105000 -> 5% return)
            assertEquals(1, leaderboard.get(0).get("rank"));
            assertEquals("traderA", leaderboard.get(0).get("username"));
            assertTrue((double) leaderboard.get(0).get("returnPercent") > 0);

            // TraderB should be #2 (equity = 100000 -> 0% return)
            assertEquals(2, leaderboard.get(1).get("rank"));
            assertEquals("traderB", leaderboard.get(1).get("username"));
        }

        @Test
        void pagedLeaderboard_returnsRequestedSlice() {
            UUID portfolioAId = UUID.randomUUID();
            UUID portfolioBId = UUID.randomUUID();
            UUID userAId = UUID.randomUUID();
            UUID userBId = UUID.randomUUID();

            TournamentParticipant pA = TournamentParticipant.builder()
                    .userId(userAId).portfolioId(portfolioAId).tournamentId(activeTournament.getId()).build();
            TournamentParticipant pB = TournamentParticipant.builder()
                    .userId(userBId).portfolioId(portfolioBId).tournamentId(activeTournament.getId()).build();

            Portfolio portfolioA = Portfolio.builder()
                    .id(portfolioAId).balance(new BigDecimal("110000")).items(List.of()).build();
            Portfolio portfolioB = Portfolio.builder()
                    .id(portfolioBId).balance(new BigDecimal("100000")).items(List.of()).build();

            when(tournamentRepository.findById(activeTournament.getId())).thenReturn(Optional.of(activeTournament));
            when(participantRepository.findByTournamentId(activeTournament.getId())).thenReturn(List.of(pA, pB));
            when(portfolioRepository.findById(portfolioAId)).thenReturn(Optional.of(portfolioA));
            when(portfolioRepository.findById(portfolioBId)).thenReturn(Optional.of(portfolioB));
            when(binanceService.getPrices()).thenReturn(Map.of());
            when(userRepository.findById(userAId)).thenReturn(Optional.of(
                    AppUser.builder().id(userAId).username("traderA").build()));
            when(userRepository.findById(userBId)).thenReturn(Optional.of(
                    AppUser.builder().id(userBId).username("traderB").build()));

            Page<Map<String, Object>> leaderboardPage = tournamentService.getTournamentLeaderboard(
                    activeTournament.getId(),
                    PageRequest.of(0, 1));

            assertEquals(1, leaderboardPage.getContent().size());
            assertEquals(2, leaderboardPage.getTotalElements());
            assertEquals("traderA", leaderboardPage.getContent().get(0).get("username"));
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void activatesUpcomingTournaments() {
            Tournament upcoming = Tournament.builder()
                    .id(UUID.randomUUID())
                    .name("Starting Now")
                    .status(Tournament.Status.UPCOMING)
                    .startsAt(LocalDateTime.now().minusMinutes(1))
                    .endsAt(LocalDateTime.now().plusDays(7))
                    .startingBalance(new BigDecimal("100000"))
                    .build();

            when(tournamentRepository.findByStatusAndStartsAtBefore(eq(Tournament.Status.UPCOMING), any()))
                    .thenReturn(List.of(upcoming));
            when(tournamentRepository.findByStatusAndEndsAtBefore(eq(Tournament.Status.ACTIVE), any()))
                    .thenReturn(List.of());

            tournamentService.manageTournamentLifecycle();

            assertEquals(Tournament.Status.ACTIVE, upcoming.getStatus());
            verify(tournamentRepository).save(upcoming);
        }
    }
}
