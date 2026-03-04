package com.finance.core.controller;

import com.finance.core.domain.PortfolioParticipant;
import com.finance.core.service.PortfolioParticipationService;
import com.finance.core.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    public ResponseEntity<Map<String, Object>> joinPortfolio(
            @PathVariable UUID portfolioId,
            @CurrentUserId UUID userId) {
        return ResponseEntity.ok(participationService.joinPortfolio(portfolioId, userId));
    }

    /** Leave a portfolio */
    @DeleteMapping("/{portfolioId}/leave")
    public ResponseEntity<Void> leavePortfolio(
            @PathVariable UUID portfolioId,
            @CurrentUserId UUID userId) {
        participationService.leavePortfolio(portfolioId, userId);
        return ResponseEntity.ok().build();
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
