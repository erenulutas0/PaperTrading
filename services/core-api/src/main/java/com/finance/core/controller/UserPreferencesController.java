package com.finance.core.controller;

import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.service.UserPreferencesService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/preferences")
@RequiredArgsConstructor
public class UserPreferencesController {

    private final UserPreferencesService userPreferencesService;

    @GetMapping
    public ResponseEntity<?> getPreferences(
            @CurrentUserId UUID userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(userPreferencesService.getPreferences(userId));
        } catch (RuntimeException exception) {
            return buildPreferencesError(exception, "user_preferences_read_failed", request);
        }
    }

    @PutMapping("/leaderboard")
    public ResponseEntity<?> updateLeaderboardPreferences(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) UpdateLeaderboardPreferencesRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(userPreferencesService.updateLeaderboardPreferences(
                    userId,
                    request));
        } catch (RuntimeException exception) {
            return buildPreferencesError(exception, "user_preferences_leaderboard_update_failed", httpRequest);
        }
    }

    @PutMapping("/terminal")
    public ResponseEntity<?> updateTerminalPreferences(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) UpdateTerminalPreferencesRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(userPreferencesService.updateTerminalPreferences(
                    userId,
                    request));
        } catch (RuntimeException exception) {
            return buildPreferencesError(exception, "user_preferences_terminal_update_failed", httpRequest);
        }
    }

    private ResponseEntity<?> buildPreferencesError(
            RuntimeException exception,
            String fallbackCode,
            HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : "";
        if ("User not found".equals(message)) {
            return ApiErrorResponses.build(
                    HttpStatus.NOT_FOUND,
                    "user_not_found",
                    message,
                    null,
                    request);
        }
        return ApiErrorResponses.build(
                HttpStatus.BAD_REQUEST,
                fallbackCode,
                message,
                null,
                request);
    }
}
