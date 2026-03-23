package com.finance.core.service;

import com.finance.core.domain.*;
import com.finance.core.repository.*;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final PortfolioRepository portfolioRepository;
    private final BadgeRepository badgeRepository;
    private final UserRepository userRepository;
    private final BinanceService binanceService;
    private final SimpMessagingTemplate messagingTemplate;

    // ... existing ...

    /** Get the latest trades from all participants in a tournament */
    public List<Map<String, Object>> getTournamentTrades(UUID tournamentId, int limit) {
        List<UUID> portfolioIds = participantRepository.findAllPortfolioIdsByTournamentId(tournamentId);
        if (portfolioIds.isEmpty())
            return List.of();

        List<TradeActivity> trades = tradeActivityRepository.findRecentTradesForPortfolios(
                portfolioIds, PageRequest.of(0, limit));

        // Batch fetch users if needed, but for now we'll map from names if we had them.
        // Let's just enrich with what we have.
        return trades.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", t.getSymbol());
            map.put("type", t.getType());
            map.put("side", t.getSide());
            map.put("price", t.getPrice());
            map.put("quantity", t.getQuantity());
            map.put("timestamp", t.getTimestamp());
            map.put("portfolioId", t.getPortfolioId());

            // Try to find username from our pre-fetched map (indirectly)
            // Ideally we'd have a userId on TradeActivity or a cache.
            // For the Combat Feed, let's keep it simple for now.
            return map;
        }).toList();
    }

    public Page<Map<String, Object>> getTournamentTrades(UUID tournamentId, Pageable pageable) {
        List<UUID> portfolioIds = participantRepository.findAllPortfolioIdsByTournamentId(tournamentId);
        if (portfolioIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<TradeActivity> trades = tradeActivityRepository.findRecentTradesForPortfolios(portfolioIds, pageable);
        long total = tradeActivityRepository.countByPortfolioIdIn(portfolioIds);

        List<Map<String, Object>> content = trades.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", t.getSymbol());
            map.put("type", t.getType());
            map.put("side", t.getSide());
            map.put("price", t.getPrice());
            map.put("quantity", t.getQuantity());
            map.put("timestamp", t.getTimestamp());
            map.put("portfolioId", t.getPortfolioId());
            return map;
        }).toList();

        return new PageImpl<>(content, pageable, total);
    }

    /** Broadcast a trade to the tournament hub if the portfolio belongs to one */
    public void notifyTournamentOfTrade(TradeActivity trade) {
        participantRepository.findByPortfolioId(trade.getPortfolioId()).ifPresent(p -> {
            String topic = "/topic/tournament/" + p.getTournamentId();
            messagingTemplate.convertAndSend(topic, Map.of(
                    "type", "TRADE",
                    "data", trade));
            log.debug("Broadcasted trade in portfolio {} to tournament {}", trade.getPortfolioId(),
                    p.getTournamentId());
        });
    }

    // ==================== CRUD ====================

    /** Create a new tournament */
    public Tournament createTournament(String name, String description,
            BigDecimal startingBalance,
            LocalDateTime startsAt, LocalDateTime endsAt) {
        Tournament tournament = Tournament.builder()
                .name(name)
                .description(description)
                .startingBalance(startingBalance != null ? startingBalance : new BigDecimal("100000"))
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(startsAt.isBefore(LocalDateTime.now()) ? Tournament.Status.ACTIVE : Tournament.Status.UPCOMING)
                .build();
        return tournamentRepository.save(tournament);
    }

    /** List all tournaments */
    public List<Tournament> getAllTournaments() {
        return tournamentRepository.findAllByOrderByStartsAtDesc();
    }

    /** List all tournaments (paged) */
    public Page<Tournament> getAllTournaments(Pageable pageable) {
        return tournamentRepository.findAllByOrderByStartsAtDesc(pageable);
    }

    /** Get active tournaments */
    public List<Tournament> getActiveTournaments() {
        return tournamentRepository.findByStatus(Tournament.Status.ACTIVE);
    }

    /** Get active tournaments (paged) */
    public Page<Tournament> getActiveTournaments(Pageable pageable) {
        return tournamentRepository.findByStatus(Tournament.Status.ACTIVE, pageable);
    }

    // ==================== JOIN ====================

    /** User joins a tournament — creates a fresh portfolio for them */
    @Transactional
    public Map<String, Object> joinTournament(UUID tournamentId, UUID userId) {
        Tournament tournament = loadRequiredTournament(tournamentId);

        if (tournament.getStatus() != Tournament.Status.ACTIVE) {
            throw ApiRequestException.conflict("tournament_not_active", "Tournament is not active. Status: " + tournament.getStatus());
        }

        if (participantRepository.existsByTournamentIdAndUserId(tournamentId, userId)) {
            throw ApiRequestException.conflict("tournament_already_joined", "Already joined this tournament");
        }

        AppUser user = loadRequiredUser(userId);

        // Create a fresh tournament portfolio
        Portfolio tournamentPortfolio = Portfolio.builder()
                .name("[T] " + tournament.getName() + " - " + user.getUsername())
                .ownerId(userId.toString())
                .description("Tournament: " + tournament.getName())
                .balance(tournament.getStartingBalance())
                .visibility(Portfolio.Visibility.PUBLIC) // Tournament portfolios are always public
                .build();
        tournamentPortfolio = portfolioRepository.save(tournamentPortfolio);

        // Record participation
        TournamentParticipant participant = TournamentParticipant.builder()
                .tournamentId(tournamentId)
                .userId(userId)
                .portfolioId(tournamentPortfolio.getId())
                .build();
        try {
            participantRepository.saveAndFlush(participant);
        } catch (DataIntegrityViolationException ex) {
            throw ApiRequestException.conflict("tournament_already_joined", "Already joined this tournament");
        }

        // Award "First Tournament" badge if this is user's first tournament
        long totalParticipations = participantRepository.findByUserId(userId).size();
        if (totalParticipations == 1) {
            badgeRepository.save(Badge.builder()
                    .userId(userId)
                    .name("First Tournament")
                    .icon("🏁")
                    .description("Joined your first paper trading tournament!")
                    .tournamentId(tournamentId)
                    .build());
        }

        log.info("User {} joined tournament {} with portfolio {}", user.getUsername(), tournament.getName(),
                tournamentPortfolio.getId());

        return Map.of(
                "message", "Joined tournament successfully!",
                "portfolioId", tournamentPortfolio.getId(),
                "tournamentName", tournament.getName(),
                "startingBalance", tournament.getStartingBalance());
    }

    /** Find participant info for a specific user and tournament */
    public Optional<TournamentParticipant> getParticipantInfo(UUID tournamentId, UUID userId) {
        return participantRepository.findByTournamentIdAndUserId(tournamentId, userId);
    }

    // ==================== LEADERBOARD ====================

    /** Get live tournament leaderboard with real-time equity calculations */
    public List<Map<String, Object>> getTournamentLeaderboard(UUID tournamentId) {
        Tournament tournament = loadRequiredTournament(tournamentId);

        List<TournamentParticipant> participants = participantRepository.findByTournamentId(tournamentId);
        if (participants.isEmpty())
            return List.of();

        // If completed, return stored results
        if (tournament.getStatus() == Tournament.Status.COMPLETED) {
            return participantRepository.findByTournamentIdOrderByFinalRankAsc(tournamentId).stream()
                    .map(p -> {
                        String username = userRepository.findById(p.getUserId())
                                .map(AppUser::getUsername).orElse("Unknown");
                        return Map.<String, Object>of(
                                "rank", p.getFinalRank() != null ? p.getFinalRank() : 0,
                                "userId", p.getUserId(),
                                "username", username,
                                "portfolioId", p.getPortfolioId(),
                                "returnPercent", p.getFinalReturnPercent() != null ? p.getFinalReturnPercent() : 0.0);
                    }).toList();
        }

        // Live calculation
        Map<String, Double> prices = binanceService.getPrices();
        BigDecimal startingBalance = tournament.getStartingBalance();

        List<Map<String, Object>> entries = new ArrayList<>();
        for (TournamentParticipant p : participants) {
            Portfolio portfolio = portfolioRepository.findById(p.getPortfolioId()).orElse(null);
            if (portfolio == null)
                continue;

            BigDecimal equity = PerformanceTrackingService.calculateTotalEquity(portfolio, prices);
            double returnPct = startingBalance.compareTo(BigDecimal.ZERO) > 0
                    ? equity.subtract(startingBalance)
                            .divide(startingBalance, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue()
                    : 0.0;

            String username = userRepository.findById(p.getUserId())
                    .map(AppUser::getUsername).orElse("Unknown");

            Map<String, Object> entry = new HashMap<>();
            entry.put("userId", p.getUserId());
            entry.put("username", username);
            entry.put("portfolioId", p.getPortfolioId());
            entry.put("equity", equity);
            entry.put("returnPercent", returnPct);
            entries.add(entry);
        }

        // Sort by return % descending
        entries.sort((a, b) -> Double.compare((double) b.get("returnPercent"), (double) a.get("returnPercent")));

        // Assign ranks
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).put("rank", i + 1);
        }

        return entries;
    }

    public Page<Map<String, Object>> getTournamentLeaderboard(UUID tournamentId, Pageable pageable) {
        List<Map<String, Object>> leaderboard = getTournamentLeaderboard(tournamentId);
        if (leaderboard.isEmpty()) {
            return Page.empty(pageable);
        }

        int total = leaderboard.size();
        int fromIndex = (int) Math.min(pageable.getOffset(), total);
        int toIndex = (int) Math.min(fromIndex + pageable.getPageSize(), total);

        if (fromIndex >= toIndex) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        return new PageImpl<>(leaderboard.subList(fromIndex, toIndex), pageable, total);
    }

    // ==================== LIFECYCLE (Scheduled) ====================

    /** Runs every 30 seconds to manage tournament state transitions */
    @Scheduled(fixedDelay = 30000)
    @SchedulerLock(name = "TournamentService.manageTournamentLifecycle", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    @Transactional
    public void manageTournamentLifecycle() {
        LocalDateTime now = LocalDateTime.now();

        // Activate upcoming tournaments
        List<Tournament> toActivate = tournamentRepository.findByStatusAndStartsAtBefore(Tournament.Status.UPCOMING,
                now);
        for (Tournament t : toActivate) {
            t.setStatus(Tournament.Status.ACTIVE);
            tournamentRepository.save(t);
            log.info("🏆 Tournament ACTIVATED: {}", t.getName());
        }

        // Complete expired tournaments
        List<Tournament> toComplete = tournamentRepository.findByStatusAndEndsAtBefore(Tournament.Status.ACTIVE, now);
        for (Tournament t : toComplete) {
            completeTournament(t);
        }
    }

    /** Finalize a tournament: calculate final rankings and award badges */
    @Transactional
    public void completeTournament(Tournament tournament) {
        tournament.setStatus(Tournament.Status.COMPLETED);
        tournamentRepository.save(tournament);

        // Calculate final standings
        List<Map<String, Object>> leaderboard = getTournamentLeaderboard(tournament.getId());
        List<TournamentParticipant> participants = participantRepository.findByTournamentId(tournament.getId());
        int totalParticipants = participants.size();

        for (Map<String, Object> entry : leaderboard) {
            UUID userId = (UUID) entry.get("userId");
            int rank = (int) entry.get("rank");
            double returnPct = (double) entry.get("returnPercent");

            // Save final results
            participantRepository.findByTournamentIdAndUserId(tournament.getId(), userId)
                    .ifPresent(p -> {
                        p.setFinalRank(rank);
                        p.setFinalReturnPercent(returnPct);
                        participantRepository.save(p);
                    });

            // Award badges
            if (rank == 1) {
                badgeRepository.save(Badge.builder()
                        .userId(userId).name("🥇 Champion").icon("🏆")
                        .description("1st place in " + tournament.getName())
                        .tournamentId(tournament.getId()).build());
            } else if (rank == 2) {
                badgeRepository.save(Badge.builder()
                        .userId(userId).name("🥈 Runner-up").icon("🥈")
                        .description("2nd place in " + tournament.getName())
                        .tournamentId(tournament.getId()).build());
            } else if (rank == 3) {
                badgeRepository.save(Badge.builder()
                        .userId(userId).name("🥉 Third Place").icon("🥉")
                        .description("3rd place in " + tournament.getName())
                        .tournamentId(tournament.getId()).build());
            }

            // Top 10% badge
            if (totalParticipants >= 10 && rank <= Math.ceil(totalParticipants * 0.1)) {
                badgeRepository.save(Badge.builder()
                        .userId(userId).name("Top 10%").icon("⭐")
                        .description("Top 10% in " + tournament.getName())
                        .tournamentId(tournament.getId()).build());
            }

            // Positive return badge
            if (returnPct > 0) {
                badgeRepository.save(Badge.builder()
                        .userId(userId).name("Profitable Trader").icon("💰")
                        .description("Finished with positive returns in " + tournament.getName())
                        .tournamentId(tournament.getId()).build());
            }
        }

        log.info("🏆 Tournament COMPLETED: {} — {} participants ranked", tournament.getName(), totalParticipants);
    }

    /** Get badges for a user */
    public List<Badge> getUserBadges(UUID userId) {
        return badgeRepository.findByUserIdOrderByEarnedAtDesc(userId);
    }

    /** Get badges for a user (paged) */
    public Page<Badge> getUserBadges(UUID userId, Pageable pageable) {
        return badgeRepository.findByUserIdOrderByEarnedAtDesc(userId, pageable);
    }

    private Tournament loadRequiredTournament(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> ApiRequestException.notFound("tournament_not_found", "Tournament not found"));
    }

    private AppUser loadRequiredUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiRequestException.notFound("user_not_found", "User not found"));
    }
}
