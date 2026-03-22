package com.finance.core.controller;

import com.finance.core.dto.AccountLeaderboardEntry;
import com.finance.core.dto.LeaderboardEntry;
import com.finance.core.service.LeaderboardService;
import com.finance.core.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/leaderboards")
@RequiredArgsConstructor
@Slf4j
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private static final Set<String> SUPPORTED_PERIODS = Set.of("1D", "1W", "1M", "ALL");
    private static final Set<String> PORTFOLIO_SORT_ALIASES = Set.of(
            "RETURN_PERCENTAGE",
            "RETURN",
            "ROI",
            "PROFIT_LOSS",
            "PROFIT");
    private static final Set<String> ACCOUNT_SORT_ALIASES = Set.of(
            "RETURN_PERCENTAGE",
            "RETURN",
            "ROI",
            "PROFIT_LOSS",
            "PROFIT",
            "WIN_RATE",
            "WIN",
            "WINRATE",
            "TRUST_SCORE",
            "TRUST",
            "TRUSTSCORE");
    private static final Set<String> DIRECTION_ALIASES = Set.of(
            "ASC",
            "DESC",
            "ASCENDING",
            "DESCENDING",
            "UP",
            "DOWN");

    @GetMapping
    public ResponseEntity<?> getLeaderboard(
            @RequestParam(defaultValue = "1D") String period,
            @RequestParam(defaultValue = "RETURN_PERCENTAGE") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        ResponseEntity<?> validationError = validatePortfolioRequest(period, sortBy, direction, httpRequest);
        if (validationError != null) {
            return validationError;
        }

        try {
            return ResponseEntity.ok(leaderboardService.getLeaderboard(period, sortBy, direction, pageable));
        } catch (Exception e) {
            log.error("Error getting leaderboard: period={}, sortBy={}, direction={}, error={}",
                    period,
                    sortBy,
                    direction,
                    e.getMessage(),
                    e);
            return ApiErrorResponses.build(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "leaderboard_fetch_failed",
                    "Failed to fetch leaderboard",
                    null,
                    httpRequest);
        }
    }

    @GetMapping("/accounts")
    public ResponseEntity<?> getAccountLeaderboard(
            @RequestParam(defaultValue = "1D") String period,
            @RequestParam(defaultValue = "RETURN_PERCENTAGE") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        ResponseEntity<?> validationError = validateAccountRequest(period, sortBy, direction, httpRequest);
        if (validationError != null) {
            return validationError;
        }

        try {
            return ResponseEntity.ok(leaderboardService.getAccountLeaderboard(period, sortBy, direction, pageable));
        } catch (Exception e) {
            log.error("Error getting account leaderboard: period={}, sortBy={}, direction={}, error={}",
                    period,
                    sortBy,
                    direction,
                    e.getMessage(),
                    e);
            return ApiErrorResponses.build(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "account_leaderboard_fetch_failed",
                    "Failed to fetch account leaderboard",
                    null,
                    httpRequest);
        }
    }

    /** Trigger a manual refresh of the leaderboard cache */
    @org.springframework.web.bind.annotation.PostMapping("/refresh")
    public ResponseEntity<?> refreshLeaderboard(HttpServletRequest httpRequest) {
        try {
            leaderboardService.refreshLeaderboardJob();
            return ResponseEntity.ok("Leaderboard refresh triggered.");
        } catch (Exception e) {
            log.error("Manual refresh failed: {}", e.getMessage(), e);
            return ApiErrorResponses.build(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "leaderboard_refresh_failed",
                    "Failed to refresh leaderboard",
                    null,
                    httpRequest);
        }
    }

    private ResponseEntity<?> validatePortfolioRequest(
            String period,
            String sortBy,
            String direction,
            HttpServletRequest request) {
        if (!isSupportedPeriod(period)) {
            return buildBadRequest("invalid_leaderboard_period", "Invalid leaderboard period", request);
        }
        if (!isSupportedAlias(sortBy, PORTFOLIO_SORT_ALIASES)) {
            return buildBadRequest("invalid_leaderboard_sort", "Invalid leaderboard sort", request);
        }
        if (!isSupportedAlias(direction, DIRECTION_ALIASES)) {
            return buildBadRequest("invalid_leaderboard_direction", "Invalid leaderboard direction", request);
        }
        return null;
    }

    private ResponseEntity<?> validateAccountRequest(
            String period,
            String sortBy,
            String direction,
            HttpServletRequest request) {
        if (!isSupportedPeriod(period)) {
            return buildBadRequest("invalid_account_leaderboard_period", "Invalid account leaderboard period", request);
        }
        if (!isSupportedAlias(sortBy, ACCOUNT_SORT_ALIASES)) {
            return buildBadRequest("invalid_account_leaderboard_sort", "Invalid account leaderboard sort", request);
        }
        if (!isSupportedAlias(direction, DIRECTION_ALIASES)) {
            return buildBadRequest("invalid_account_leaderboard_direction", "Invalid account leaderboard direction", request);
        }
        return null;
    }

    private boolean isSupportedPeriod(String period) {
        return isSupportedAlias(period, SUPPORTED_PERIODS);
    }

    private boolean isSupportedAlias(String value, Set<String> supportedValues) {
        if (value == null) {
            return false;
        }
        return supportedValues.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private ResponseEntity<?> buildBadRequest(String code, String message, HttpServletRequest request) {
        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, code, message, null, request);
    }
}
