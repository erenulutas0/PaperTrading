package com.finance.core.controller;

import com.finance.core.domain.Badge;
import com.finance.core.domain.Tournament;
import com.finance.core.service.TournamentService;
import com.finance.core.repository.TournamentRepository;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;
    private final TournamentRepository tournamentRepository;

    /** List all tournaments */
    @GetMapping
    public ResponseEntity<Page<Tournament>> getAllTournaments(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tournamentService.getAllTournaments(pageable));
    }

    /** List active tournaments */
    @GetMapping("/active")
    public ResponseEntity<Page<Tournament>> getActiveTournaments(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tournamentService.getActiveTournaments(pageable));
    }

    /** Create a new tournament (admin-like) */
    @PostMapping
    public ResponseEntity<?> createTournament(@RequestBody CreateTournamentRequest request, HttpServletRequest httpRequest) {
        try {
            Tournament tournament = tournamentService.createTournament(
                    request.getName(),
                    request.getDescription(),
                    request.getStartingBalance(),
                    request.getStartsAt(),
                    request.getEndsAt());
            return ResponseEntity.ok(tournament);
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_create_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Join a tournament */
    @PostMapping("/{tournamentId}/join")
    public ResponseEntity<?> joinTournament(
            @PathVariable UUID tournamentId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            Map<String, Object> result = tournamentService.joinTournament(tournamentId, userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_join_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Get user's participation info for a tournament */
    @GetMapping("/{tournamentId}/participant")
    public ResponseEntity<?> getParticipantInfo(
            @PathVariable UUID tournamentId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        return tournamentService.getParticipantInfo(tournamentId, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ApiErrorResponses.build(
                        HttpStatus.NOT_FOUND,
                        "tournament_participant_not_found",
                        "Tournament participant not found",
                        null,
                        httpRequest));
    }

    /** Get live tournament leaderboard */
    @GetMapping("/{tournamentId}/leaderboard")
    public ResponseEntity<?> getLeaderboard(@PathVariable UUID tournamentId, HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(tournamentService.getTournamentLeaderboard(tournamentId));
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_leaderboard_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Get tournament details */
    @GetMapping("/{tournamentId}")
    public ResponseEntity<?> getTournament(@PathVariable UUID tournamentId, HttpServletRequest httpRequest) {
        return tournamentRepository.findById(tournamentId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ApiErrorResponses.build(
                        HttpStatus.NOT_FOUND,
                        "tournament_not_found",
                        "Tournament not found",
                        null,
                        httpRequest));
    }

    /** Get live tournament combat feed (recent trades) */
    @GetMapping("/{tournamentId}/trades")
    public ResponseEntity<?> getTournamentTrades(
            @PathVariable UUID tournamentId,
            @RequestParam(defaultValue = "15") int limit,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(tournamentService.getTournamentTrades(tournamentId, limit));
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_trades_failed", e.getMessage(), null, httpRequest);
        }
    }

    /** Get user's earned badges */
    @GetMapping("/badges/{userId}")
    public ResponseEntity<List<Badge>> getUserBadges(@PathVariable UUID userId) {
        return ResponseEntity.ok(tournamentService.getUserBadges(userId));
    }

    @Data
    static class CreateTournamentRequest {
        private String name;
        private String description;
        private BigDecimal startingBalance;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
    }
}
