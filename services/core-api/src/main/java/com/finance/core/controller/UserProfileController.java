package com.finance.core.controller;

import com.finance.core.dto.UpdateProfileRequest;
import com.finance.core.dto.UserProfileResponse;
import com.finance.core.service.UserProfileService;
import com.finance.core.web.ApiErrorResponses;
import com.finance.core.web.ApiRequestException;
import com.finance.core.web.CurrentUserId;
import com.finance.core.web.PageableRequestParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(org.springframework.http.HttpStatus.BAD_REQUEST, "follow_failed", "Failed to follow user", null, httpRequest);
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
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception e) {
            return ApiErrorResponses.build(org.springframework.http.HttpStatus.BAD_REQUEST, "unfollow_failed", "Failed to unfollow user", null, httpRequest);
        }
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<Page<UserProfileResponse>> getFollowers(
            @PathVariable UUID userId,
            @CurrentUserId(required = false) UUID requesterId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = resolveFollowPageable(pageable, page, size);
        return ResponseEntity.ok(userProfileService.getFollowers(userId, requesterId, effectivePageable));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<Page<UserProfileResponse>> getFollowing(
            @PathVariable UUID userId,
            @CurrentUserId(required = false) UUID requesterId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @PageableDefault(size = 20) Pageable pageable) {
        Pageable effectivePageable = resolveFollowPageable(pageable, page, size);
        return ResponseEntity.ok(userProfileService.getFollowing(userId, requesterId, effectivePageable));
    }

    private Pageable resolveFollowPageable(Pageable pageable, String rawPage, String rawSize) {
        return PageableRequestParser.resolvePageable(
                pageable,
                rawPage,
                rawSize,
                "invalid_user_follow_page",
                "Invalid user follow page",
                "invalid_user_follow_size",
                "Invalid user follow size");
    }
}
