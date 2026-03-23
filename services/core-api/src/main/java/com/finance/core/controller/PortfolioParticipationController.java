package com.finance.core.controller;

import com.finance.core.domain.PortfolioParticipant;
import com.finance.core.service.PortfolioParticipationService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "portfolio_join_failed", "Failed to join portfolio", null, httpRequest);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "portfolio_leave_failed", "Failed to leave portfolio", null, httpRequest);
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
}
