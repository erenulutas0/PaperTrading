package com.finance.core.service;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.domain.AuditActionType;
import com.finance.core.domain.AuditResourceType;
import com.finance.core.domain.AppUser;
import com.finance.core.domain.Follow;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.UpdateProfileRequest;
import com.finance.core.dto.TrustScoreBreakdownResponse;
import com.finance.core.dto.UserProfileResponse;
import com.finance.core.dto.UserSuggestionResponse;
import com.finance.core.repository.FollowRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import com.finance.core.web.ApiRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.core.domain.Notification.NotificationType;
import com.finance.core.domain.event.NotificationEvent;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

        private final UserRepository userRepository;
        private final FollowRepository followRepository;
        private final PortfolioRepository portfolioRepository;
        private final ActivityFeedService activityFeedService;
        private final TrustScoreService trustScoreService;
        private final ApplicationEventPublisher eventPublisher;
        private final AuditLogService auditLogService;

        public UserProfileResponse getProfile(UUID userId, UUID requesterId) {
                AppUser user = userRepository.findById(userId)
                                .orElseThrow(() -> ApiRequestException.notFound("user_not_found", "User not found"));

                long portfolioCount = portfolioRepository.findByOwnerId(userId.toString()).stream()
                                .filter(p -> p.getVisibility() == Portfolio.Visibility.PUBLIC ||
                                                userId.toString().equals(
                                                                requesterId != null ? requesterId.toString() : ""))
                                .count();

                boolean isFollowing = requesterId != null &&
                                followRepository.existsByFollowerIdAndFollowingId(requesterId, userId);

                TrustScoreBreakdownResponse trustBreakdown = trustScoreService.buildTrustScoreBreakdown(userId);
                double trustScore = trustScoreService.calculateTrustScore(trustBreakdown);
                double trustScoreChange7d = trustScoreService.calculateTrustScoreChange7d(userId, trustScore);
                double winRateChange7d = trustScoreService.calculateWinRateChange7d(userId, trustBreakdown);

                return UserProfileResponse.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                                .bio(user.getBio())
                                .avatarUrl(user.getAvatarUrl())
                                .verified(user.isVerified())
                                .followerCount(user.getFollowerCount())
                                .followingCount(user.getFollowingCount())
                                .portfolioCount((int) portfolioCount)
                                .isFollowing(isFollowing)
                                .trustScore(trustScore)
                                .winRate(trustBreakdown.getBlendedWinRate())
                                .trustScoreChange7d(trustScoreChange7d)
                                .winRateChange7d(winRateChange7d)
                                .trustBreakdown(trustBreakdown)
                                .trustHistory(trustScoreService.buildTrustHistory(userId, trustBreakdown, trustScore, 30))
                                .memberSince(user.getCreatedAt())
                                .build();
        }

        @Transactional
        public void updateProfile(UUID userId, UpdateProfileRequest request) {
                AppUser user = userRepository.findById(userId)
                                .orElseThrow(() -> ApiRequestException.notFound("user_not_found", "User not found"));

                if (request.getDisplayName() != null)
                        user.setDisplayName(request.getDisplayName());
                if (request.getBio() != null)
                        user.setBio(request.getBio());
                if (request.getAvatarUrl() != null)
                        user.setAvatarUrl(request.getAvatarUrl());

                userRepository.save(user);
                log.info("Profile updated for user {}", userId);
        }

        @Transactional
        public void follow(UUID followerId, UUID followingId) {
                if (followerId.equals(followingId)) {
                        throw ApiRequestException.badRequest("cannot_follow_self", "Cannot follow yourself");
                }
                if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
                        throw ApiRequestException.conflict("already_following", "Already following");
                }

                loadRequiredUser(followerId, "follower_not_found", "Follower not found");
                loadRequiredUser(followingId, "user_not_found", "User to follow not found");

                try {
                        followRepository.save(Follow.builder()
                                        .followerId(followerId)
                                        .followingId(followingId)
                                        .build());
                } catch (DataIntegrityViolationException ex) {
                        throw ApiRequestException.conflict("already_following", "Already following");
                }

                adjustFollowCounters(followerId, followingId, 1);
                AppUser refreshedFollower = loadRequiredUser(followerId, "follower_not_found", "Follower not found");
                AppUser refreshedFollowing = loadRequiredUser(followingId, "user_not_found", "User to follow not found");

                // Publish follow event to activity feed
                activityFeedService.publish(
                                followerId, refreshedFollower.getUsername(),
                                ActivityEvent.EventType.FOLLOW,
                                ActivityEvent.TargetType.USER,
                                followingId, refreshedFollowing.getUsername());

                // Publish real-time notification
                eventPublisher.publishEvent(NotificationEvent.builder()
                                .receiverId(followingId)
                                .actorId(followerId)
                                .actorUsername(refreshedFollower.getUsername())
                                .type(NotificationType.FOLLOW)
                                .referenceId(followerId)
                                .referenceLabel(refreshedFollower.getUsername())
                                .build());

                auditLogService.record(
                                followerId,
                                AuditActionType.USER_FOLLOWED,
                                AuditResourceType.USER,
                                followingId,
                                Map.of(
                                                "followerUsername", refreshedFollower.getUsername(),
                                                "followingUsername", refreshedFollowing.getUsername()));

                log.info("User {} followed user {}", followerId, followingId);
        }

        @Transactional
        public void unfollow(UUID followerId, UUID followingId) {
                int deleted = followRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
                if (deleted == 0) {
                        throw ApiRequestException.notFound("follow_not_found", "Not following this user");
                }

                adjustFollowCounters(followerId, followingId, -1);
                AppUser follower = loadRequiredUser(followerId, "follower_not_found", "Follower not found");
                AppUser following = loadRequiredUser(followingId, "user_not_found", "User to unfollow not found");

                auditLogService.record(
                                followerId,
                                AuditActionType.USER_UNFOLLOWED,
                                AuditResourceType.USER,
                                followingId,
                                Map.of(
                                                "followerUsername", follower.getUsername(),
                                                "followingUsername", following.getUsername()));

                log.info("User {} unfollowed user {}", followerId, followingId);
        }

        private void adjustFollowCounters(UUID followerId, UUID followingId, int delta) {
                if (userRepository.adjustFollowingCount(followerId, delta) == 0) {
                        throw ApiRequestException.notFound("follower_not_found", "Follower not found");
                }
                if (userRepository.adjustFollowerCount(followingId, delta) == 0) {
                        throw ApiRequestException.notFound("user_not_found",
                                        delta > 0 ? "User to follow not found" : "User to unfollow not found");
                }
        }

        private AppUser loadRequiredUser(UUID userId, String code, String message) {
                return userRepository.findById(userId)
                                .orElseThrow(() -> ApiRequestException.notFound(code, message));
        }

        public Page<UserProfileResponse> getFollowers(UUID userId, UUID requesterId, Pageable pageable) {
                return followRepository.findByFollowingId(userId, pageable)
                                .map(f -> getProfile(f.getFollowerId(), requesterId));
        }

        public Page<UserProfileResponse> getFollowing(UUID userId, UUID requesterId, Pageable pageable) {
                return followRepository.findByFollowerId(userId, pageable)
                                .map(f -> getProfile(f.getFollowingId(), requesterId));
        }

        public List<UserSuggestionResponse> getSuggestedAccounts(UUID requesterId, int limit) {
                int effectiveLimit = Math.max(1, Math.min(limit, 12));
                List<AppUser> candidates = userRepository.findSuggestedAccounts(PageRequest.of(0, effectiveLimit * 4));
                Set<UUID> followedIds = requesterId == null
                                ? Set.of()
                                : followRepository.findByFollowerId(requesterId).stream()
                                                .map(Follow::getFollowingId)
                                                .collect(java.util.stream.Collectors.toCollection(HashSet::new));

                List<AppUser> filteredUsers = candidates.stream()
                                .filter(user -> requesterId == null || !user.getId().equals(requesterId))
                                .filter(user -> !followedIds.contains(user.getId()))
                                .toList();
                if (filteredUsers.isEmpty()) {
                        return List.of();
                }

                Map<String, Integer> publicPortfolioCounts = new HashMap<>();
                List<Object[]> groupedCounts = portfolioRepository.countByOwnerIdInAndVisibilityGrouped(
                                filteredUsers.stream().map(user -> user.getId().toString()).toList(),
                                Portfolio.Visibility.PUBLIC);
                for (Object[] row : groupedCounts) {
                        if (row.length >= 2 && row[0] != null && row[1] instanceof Number count) {
                                publicPortfolioCounts.put(String.valueOf(row[0]), count.intValue());
                        }
                }

                return filteredUsers.stream()
                                .filter(user -> publicPortfolioCounts.getOrDefault(user.getId().toString(), 0) > 0)
                                .limit(effectiveLimit)
                                .map(user -> UserSuggestionResponse.builder()
                                                .id(user.getId())
                                                .username(user.getUsername())
                                                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                                                .avatarUrl(user.getAvatarUrl())
                                                .verified(user.isVerified())
                                                .followerCount(user.getFollowerCount())
                                                .portfolioCount(publicPortfolioCounts.getOrDefault(user.getId().toString(), 0))
                                                .trustScore(user.getTrustScore())
                                                .following(requesterId != null && followedIds.contains(user.getId()))
                                                .build())
                                .toList();
        }
}
