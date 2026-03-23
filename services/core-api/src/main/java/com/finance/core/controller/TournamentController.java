package com.finance.core.controller;

import com.finance.core.domain.Badge;
import com.finance.core.domain.Tournament;
import com.finance.core.service.TournamentService;
import com.finance.core.repository.TournamentRepository;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.PageableRequestParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    public ResponseEntity<Page<Tournament>> getAllTournaments(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = resolveTournamentPageable(pageable, page, size);
        return ResponseEntity.ok(tournamentService.getAllTournaments(effectivePageable));
    }

    /** List active tournaments */
    @GetMapping("/active")
    public ResponseEntity<Page<Tournament>> getActiveTournaments(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = resolveTournamentPageable(pageable, page, size);
        return ResponseEntity.ok(tournamentService.getActiveTournaments(effectivePageable));
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_create_failed", "Failed to create tournament", null, httpRequest);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_join_failed", "Failed to join tournament", null, httpRequest);
        }
    }

    /** Get user's participation info for a tournament */
    @GetMapping("/{tournamentId}/participant")
    public ResponseEntity<?> getParticipantInfo(
            @PathVariable UUID tournamentId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        return tournamentService.getParticipantInfo(tournamentId, userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ApiErrorResponses.build(
                        HttpStatus.NOT_FOUND,
                        "tournament_participant_not_found",
                        "Tournament participant not found",
                        null,
                        httpRequest));
    }

    /** Get live tournament leaderboard */
    @GetMapping("/{tournamentId}/leaderboard")
    public ResponseEntity<?> getLeaderboard(
            @PathVariable UUID tournamentId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        Pageable effectivePageable = resolveTournamentPageable(pageable, page, size);
        try {
            return ResponseEntity.ok(tournamentService.getTournamentLeaderboard(tournamentId, effectivePageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_leaderboard_failed", "Failed to load tournament leaderboard", null, httpRequest);
        }
    }

    /** Get tournament details */
    @GetMapping("/{tournamentId}")
    public ResponseEntity<?> getTournament(@PathVariable UUID tournamentId, HttpServletRequest httpRequest) {
        return tournamentRepository.findById(tournamentId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
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
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 15) Pageable pageable,
            @RequestParam(required = false) String limit,
            HttpServletRequest httpRequest) {
        try {
            Pageable effectivePageable = resolveTournamentPageable(pageable, page, size);
            Integer parsedLimit = parseTournamentTradesLimit(limit);
            if (parsedLimit != null) {
                effectivePageable = PageRequest.of(effectivePageable.getPageNumber(), parsedLimit, effectivePageable.getSort());
            }
            return ResponseEntity.ok(tournamentService.getTournamentTrades(tournamentId, effectivePageable));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "tournament_trades_failed", "Failed to load tournament trades", null, httpRequest);
        }
    }

    /** Get user's earned badges */
    @GetMapping("/badges/{userId}")
    public ResponseEntity<Page<Badge>> getUserBadges(
            @PathVariable UUID userId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = resolveTournamentPageable(pageable, page, size);
        return ResponseEntity.ok(tournamentService.getUserBadges(userId, effectivePageable));
    }

    @Data
    static class CreateTournamentRequest {
        private String name;
        private String description;
        private BigDecimal startingBalance;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
    }

    private Integer parseTournamentTradesLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(rawLimit.trim());
        } catch (NumberFormatException exception) {
            throw ApiRequestException.badRequest(
                    "invalid_tournament_trades_limit",
                    "Tournament trades limit must be an integer between 1 and 100");
        }
        if (parsed < 1 || parsed > 100) {
            throw ApiRequestException.badRequest(
                    "invalid_tournament_trades_limit",
                    "Tournament trades limit must be an integer between 1 and 100");
        }
        return parsed;
    }

    private Pageable resolveTournamentPageable(Pageable pageable, String rawPage, String rawSize) {
        return PageableRequestParser.resolvePageable(
                pageable,
                rawPage,
                rawSize,
                "invalid_tournament_page",
                "Invalid tournament page",
                "invalid_tournament_size",
                "Invalid tournament size");
    }
}
