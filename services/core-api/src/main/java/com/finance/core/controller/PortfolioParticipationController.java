package com.finance.core.controller;

import com.finance.core.domain.PortfolioParticipant;
import com.finance.core.service.PortfolioParticipationService;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
public class PortfolioParticipationController {

    private final PortfolioParticipationService participationService;

    /** Join a public portfolio — creates a clone for the user */
    @PostMapping("/{portfolioId}/join")
    public ResponseEntity<?> joinPortfolio(
            @PathVariable UUID portfolioId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(participationService.joinPortfolio(portfolioId, userId));
        } catch (Exception e) {
            return buildParticipationError(e, "portfolio_join_failed", "Failed to join portfolio", httpRequest);
        }
    }

    /** Leave a portfolio */
    @DeleteMapping("/{portfolioId}/leave")
    public ResponseEntity<?> leavePortfolio(
            @PathVariable UUID portfolioId,
            @CurrentUserId UUID userId,
            HttpServletRequest httpRequest) {
        try {
            participationService.leavePortfolio(portfolioId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return buildParticipationError(e, "portfolio_leave_failed", "Failed to leave portfolio", httpRequest);
        }
    }

    /** Get participants of a portfolio (paginated) */
    @GetMapping("/{portfolioId}/participants")
    public ResponseEntity<Page<PortfolioParticipant>> getParticipants(
            @PathVariable UUID portfolioId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(participationService.getParticipants(portfolioId, pageable));
    }

    /** Get participation stats for a portfolio */
    @GetMapping("/{portfolioId}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable UUID portfolioId) {
        long count = participationService.getParticipantCount(portfolioId);
        return ResponseEntity.ok(Map.of("participantCount", count));
    }

    private ResponseEntity<?> buildParticipationError(Exception exception, String fallbackCode, String fallbackMessage,
            HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : fallbackMessage;
        String normalized = message.toLowerCase();

        if (normalized.contains("portfolio not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "portfolio_not_found", "Portfolio not found", null,
                    request);
        }
        if (normalized.contains("cannot join a private portfolio")) {
            return ApiErrorResponses.build(HttpStatus.CONFLICT, "portfolio_private", "Cannot join a private portfolio",
                    null, request);
        }
        if (normalized.contains("cannot join your own portfolio")) {
            return ApiErrorResponses.build(HttpStatus.CONFLICT, "cannot_join_own_portfolio",
                    "Cannot join your own portfolio", null, request);
        }
        if (normalized.contains("already joined this portfolio")) {
            return ApiErrorResponses.build(HttpStatus.CONFLICT, "portfolio_already_joined",
                    "Already joined this portfolio", null, request);
        }
        if (normalized.contains("not a participant of this portfolio")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "portfolio_participation_not_found",
                    "Not a participant of this portfolio", null, request);
        }
        if (normalized.contains("user not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "user_not_found", "User not found", null, request);
        }

        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, fallbackCode, message, null, request);
    }
}
