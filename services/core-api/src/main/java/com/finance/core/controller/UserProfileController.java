package com.finance.core.controller;

import com.finance.core.dto.UpdateProfileRequest;
import com.finance.core.dto.UserProfileResponse;
import com.finance.core.service.UserProfileService;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            @PathVariable UUID userId,
            @CurrentUserId(required = false) UUID requesterId) {
        return ResponseEntity.ok(userProfileService.getProfile(userId, requesterId));
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<Void> updateProfile(
            @PathVariable UUID userId,
            @RequestBody UpdateProfileRequest request) {
        userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/follow")
    public ResponseEntity<?> follow(
            @PathVariable UUID userId,
            @CurrentUserId UUID followerId,
            HttpServletRequest httpRequest) {
        try {
            userProfileService.follow(followerId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return buildFollowError(e, "follow_failed", "Failed to follow user", httpRequest);
        }
    }

    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<?> unfollow(
            @PathVariable UUID userId,
            @CurrentUserId UUID followerId,
            HttpServletRequest httpRequest) {
        try {
            userProfileService.unfollow(followerId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return buildFollowError(e, "unfollow_failed", "Failed to unfollow user", httpRequest);
        }
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<Page<UserProfileResponse>> getFollowers(
            @PathVariable UUID userId,
            @CurrentUserId(required = false) UUID requesterId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userProfileService.getFollowers(userId, requesterId, pageable));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<Page<UserProfileResponse>> getFollowing(
            @PathVariable UUID userId,
            @CurrentUserId(required = false) UUID requesterId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userProfileService.getFollowing(userId, requesterId, pageable));
    }

    private ResponseEntity<?> buildFollowError(Exception exception, String fallbackCode, String fallbackMessage,
            HttpServletRequest request) {
        String message = exception.getMessage() != null ? exception.getMessage() : fallbackMessage;
        String normalized = message.toLowerCase();

        if (normalized.contains("cannot follow yourself")) {
            return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, "cannot_follow_self", "Cannot follow yourself", null,
                    request);
        }
        if (normalized.contains("already following")) {
            return ApiErrorResponses.build(HttpStatus.CONFLICT, "already_following", "Already following", null,
                    request);
        }
        if (normalized.contains("follower not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "follower_not_found", "Follower not found", null,
                    request);
        }
        if (normalized.contains("user to follow not found")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "user_not_found", "User to follow not found", null,
                    request);
        }
        if (normalized.contains("not following this user")) {
            return ApiErrorResponses.build(HttpStatus.NOT_FOUND, "follow_not_found", "Not following this user", null,
                    request);
        }

        return ApiErrorResponses.build(HttpStatus.BAD_REQUEST, fallbackCode, message, null, request);
    }
}
