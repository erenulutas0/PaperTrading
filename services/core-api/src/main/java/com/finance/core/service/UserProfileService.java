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
import com.finance.core.repository.FollowRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finance.core.domain.Notification.NotificationType;
import com.finance.core.domain.event.NotificationEvent;

import java.util.List;
import java.util.Map;
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
                                .orElseThrow(() -> new RuntimeException("User not found"));

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
                                .orElseThrow(() -> new RuntimeException("User not found"));

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
                        throw new RuntimeException("Cannot follow yourself");
                }
                if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
                        throw new RuntimeException("Already following");
                }

                // Verify both users exist
                userRepository.findById(followerId)
                                .orElseThrow(() -> new RuntimeException("Follower not found"));
                AppUser following = userRepository.findById(followingId)
                                .orElseThrow(() -> new RuntimeException("User to follow not found"));

                followRepository.save(Follow.builder()
                                .followerId(followerId)
                                .followingId(followingId)
                                .build());

                // Update denormalized counters
                AppUser follower = userRepository.findById(followerId).get();
                follower.setFollowingCount(follower.getFollowingCount() + 1);
                following.setFollowerCount(following.getFollowerCount() + 1);
                userRepository.save(follower);
                userRepository.save(following);

                // Publish follow event to activity feed
                activityFeedService.publish(
                                followerId, follower.getUsername(),
                                ActivityEvent.EventType.FOLLOW,
                                ActivityEvent.TargetType.USER,
                                followingId, following.getUsername());

                // Publish real-time notification
                eventPublisher.publishEvent(NotificationEvent.builder()
                                .receiverId(followingId)
                                .actorId(followerId)
                                .actorUsername(follower.getUsername())
                                .type(NotificationType.FOLLOW)
                                .referenceId(followerId)
                                .referenceLabel(follower.getUsername())
                                .build());

                auditLogService.record(
                                followerId,
                                AuditActionType.USER_FOLLOWED,
                                AuditResourceType.USER,
                                followingId,
                                Map.of(
                                                "followerUsername", follower.getUsername(),
                                                "followingUsername", following.getUsername()));

                log.info("User {} followed user {}", followerId, followingId);
        }

        @Transactional
        public void unfollow(UUID followerId, UUID followingId) {
                Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                                .orElseThrow(() -> new RuntimeException("Not following this user"));

                followRepository.delete(follow);

                // Update denormalized counters
                AppUser follower = userRepository.findById(followerId).get();
                AppUser following = userRepository.findById(followingId).get();
                follower.setFollowingCount(Math.max(0, follower.getFollowingCount() - 1));
                following.setFollowerCount(Math.max(0, following.getFollowerCount() - 1));
                userRepository.save(follower);
                userRepository.save(following);

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

        public Page<UserProfileResponse> getFollowers(UUID userId, UUID requesterId, Pageable pageable) {
                return followRepository.findByFollowingId(userId, pageable)
                                .map(f -> getProfile(f.getFollowerId(), requesterId));
        }

        public Page<UserProfileResponse> getFollowing(UUID userId, UUID requesterId, Pageable pageable) {
                return followRepository.findByFollowerId(userId, pageable)
                                .map(f -> getProfile(f.getFollowingId(), requesterId));
        }
}
