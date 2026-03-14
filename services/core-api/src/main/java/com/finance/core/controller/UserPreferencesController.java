package com.finance.core.controller;

import com.finance.core.dto.UpdateTerminalPreferencesRequest;
import com.finance.core.dto.UpdateLeaderboardPreferencesRequest;
import com.finance.core.dto.UserPreferencesResponse;
import com.finance.core.service.UserPreferencesService;
import com.finance.core.web.CurrentUserId;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<UserPreferencesResponse> getPreferences(
            @CurrentUserId UUID userId) {
        return ResponseEntity.ok(userPreferencesService.getPreferences(userId));
    }

    @PutMapping("/leaderboard")
    public ResponseEntity<UserPreferencesResponse> updateLeaderboardPreferences(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) UpdateLeaderboardPreferencesRequest request) {
        return ResponseEntity.ok(userPreferencesService.updateLeaderboardPreferences(
                userId,
                request));
    }

    @PutMapping("/terminal")
    public ResponseEntity<UserPreferencesResponse> updateTerminalPreferences(
            @CurrentUserId UUID userId,
            @RequestBody(required = false) UpdateTerminalPreferencesRequest request) {
        return ResponseEntity.ok(userPreferencesService.updateTerminalPreferences(
                userId,
                request));
    }
}
