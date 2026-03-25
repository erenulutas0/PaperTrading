package com.finance.core.controller;

import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UpdateNotificationPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.service.UserPreferencesService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "user_preferences_read_failed", "Failed to read user preferences", null, request);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "user_preferences_leaderboard_update_failed", "Failed to update leaderboard preferences", null, httpRequest);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "user_preferences_terminal_update_failed", "Failed to update terminal preferences", null, httpRequest);
        }
    }

    @PutMapping("/notifications")
    public ResponseEntity<?> updateNotificationPreferences(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) UpdateNotificationPreferencesRequest request,
            HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.ok(userPreferencesService.updateNotificationPreferences(userId, request));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "user_preferences_notification_update_failed", "Failed to update notification preferences", null, httpRequest);
        }
    }
}
