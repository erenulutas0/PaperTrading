package com.finance.core.controller;

import com.finance.core.dto.LeaderboardEntry;
import com.finance.core.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/leaderboards")
@RequiredArgsConstructor
@Slf4j
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping
    public ResponseEntity<Page<LeaderboardEntry>> getLeaderboard(
            @RequestParam(defaultValue = "1D") String period,
            @RequestParam(defaultValue = "RETURN_PERCENTAGE") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @PageableDefault(size = 20) Pageable pageable) {
        try {
            return ResponseEntity.ok(leaderboardService.getLeaderboard(period, sortBy, direction, pageable));
        } catch (Exception e) {
            log.error("Error getting leaderboard: period={}, sortBy={}, direction={}, error={}",
                    period,
                    sortBy,
                    direction,
                    e.getMessage(),
                    e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Trigger a manual refresh of the leaderboard cache */
    @org.springframework.web.bind.annotation.PostMapping("/refresh")
    public ResponseEntity<String> refreshLeaderboard() {
        try {
            leaderboardService.refreshLeaderboardJob();
            return ResponseEntity.ok("Leaderboard refresh triggered.");
        } catch (Exception e) {
            log.error("Manual refresh failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
