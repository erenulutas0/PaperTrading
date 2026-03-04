package com.finance.core.service;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.Follow;
import com.finance.core.domain.Portfolio;
import com.finance.core.dto.UpdateProfileRequest;
import com.finance.core.dto.UserProfileResponse;
import com.finance.core.repository.FollowRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.PortfolioRepository;
import com.finance.core.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

        @Mock
        private UserRepository userRepository;
        @Mock
        private FollowRepository followRepository;
        @Mock
        private PortfolioRepository portfolioRepository;
        @Mock
        private ActivityFeedService activityFeedService;
        @Mock
        private PerformanceAnalyticsService performanceAnalyticsService;
        @Mock
        private org.springframework.context.ApplicationEventPublisher eventPublisher;

        @InjectMocks
        private UserProfileService userProfileService;

        private UUID userAId;
        private UUID userBId;
        private AppUser userA;
        private AppUser userB;

        @BeforeEach
        void setUp() {
                userAId = UUID.randomUUID();
                userBId = UUID.randomUUID();

                userA = AppUser.builder()
                                .id(userAId)
                                .username("alice")
                                .email("alice@test.com")
                                .password("pass")
                                .displayName("Alice Trader")
                                .bio("Crypto enthusiast")
                                .followerCount(0)
                                .followingCount(0)
                                .verified(false)
                                .createdAt(LocalDateTime.now())
                                .build();

                userB = AppUser.builder()
                                .id(userBId)
                                .username("bob")
                                .email("bob@test.com")
                                .password("pass")
                                .followerCount(0)
                                .followingCount(0)
                                .verified(true)
                                .createdAt(LocalDateTime.now())
                                .build();
        }

        // ===== PROFILE RETRIEVAL =====

        @Test
        void getProfile_returnsCorrectData() {
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(portfolioRepository.findByOwnerId(userAId.toString())).thenReturn(List.of());
                // Note: followRepository.existsByFollowerIdAndFollowingId is NOT called when
                // requesterId is null

                UserProfileResponse response = userProfileService.getProfile(userAId, null);

                assertEquals(userAId, response.getId());
                assertEquals("alice", response.getUsername());
                assertEquals("Alice Trader", response.getDisplayName());
                assertEquals("Crypto enthusiast", response.getBio());
                assertFalse(response.isVerified());
                assertEquals(0, response.getFollowerCount());
                assertEquals(0, response.getPortfolioCount());
                assertFalse(response.isFollowing());
        }

        @Test
        void getProfile_fallsBackToUsernameWhenNoDisplayName() {
                userA.setDisplayName(null);
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(portfolioRepository.findByOwnerId(userAId.toString())).thenReturn(List.of());

                UserProfileResponse response = userProfileService.getProfile(userAId, null);

                assertEquals("alice", response.getDisplayName());
        }

        @Test
        void getProfile_showsIsFollowingTrue_whenRequesterFollows() {
                when(userRepository.findById(userBId)).thenReturn(Optional.of(userB));
                when(portfolioRepository.findByOwnerId(userBId.toString())).thenReturn(List.of());
                when(followRepository.existsByFollowerIdAndFollowingId(userAId, userBId)).thenReturn(true);

                UserProfileResponse response = userProfileService.getProfile(userBId, userAId);

                assertTrue(response.isFollowing());
        }

        @Test
        void getProfile_countsOnlyPublicPortfoliosForOtherUsers() {
                Portfolio publicPortfolio = Portfolio.builder()
                                .id(UUID.randomUUID()).name("Public").ownerId(userAId.toString())
                                .visibility(Portfolio.Visibility.PUBLIC).build();
                Portfolio privatePortfolio = Portfolio.builder()
                                .id(UUID.randomUUID()).name("Private").ownerId(userAId.toString())
                                .visibility(Portfolio.Visibility.PRIVATE).build();

                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(portfolioRepository.findByOwnerId(userAId.toString()))
                                .thenReturn(List.of(publicPortfolio, privatePortfolio));

                // When another user views: should only see public (1)
                UserProfileResponse response = userProfileService.getProfile(userAId, userBId);
                assertEquals(1, response.getPortfolioCount());
        }

        @Test
        void getProfile_countsAllPortfoliosForOwnProfile() {
                Portfolio publicPortfolio = Portfolio.builder()
                                .id(UUID.randomUUID()).name("Public").ownerId(userAId.toString())
                                .visibility(Portfolio.Visibility.PUBLIC).build();
                Portfolio privatePortfolio = Portfolio.builder()
                                .id(UUID.randomUUID()).name("Private").ownerId(userAId.toString())
                                .visibility(Portfolio.Visibility.PRIVATE).build();

                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(portfolioRepository.findByOwnerId(userAId.toString()))
                                .thenReturn(List.of(publicPortfolio, privatePortfolio));
                when(followRepository.existsByFollowerIdAndFollowingId(userAId, userAId)).thenReturn(false);

                // When owner views own profile: should see all (2)
                UserProfileResponse response = userProfileService.getProfile(userAId, userAId);
                assertEquals(2, response.getPortfolioCount());
        }

        @Test
        void getProfile_throwsWhenUserNotFound() {
                UUID fakeId = UUID.randomUUID();
                when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

                assertThrows(RuntimeException.class, () -> userProfileService.getProfile(fakeId, null));
        }

        // ===== PROFILE UPDATE =====

        @Test
        void updateProfile_updatesDisplayName() {
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(userRepository.save(any())).thenReturn(userA);

                UpdateProfileRequest request = new UpdateProfileRequest();
                request.setDisplayName("Alice Updated");
                userProfileService.updateProfile(userAId, request);

                ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
                verify(userRepository).save(captor.capture());
                assertEquals("Alice Updated", captor.getValue().getDisplayName());
        }

        @Test
        void updateProfile_updatesOnlyNonNullFields() {
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(userRepository.save(any())).thenReturn(userA);

                UpdateProfileRequest request = new UpdateProfileRequest();
                request.setBio("New bio here");
                // displayName and avatarUrl are null — should NOT overwrite
                userProfileService.updateProfile(userAId, request);

                ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
                verify(userRepository).save(captor.capture());
                assertEquals("Alice Trader", captor.getValue().getDisplayName()); // unchanged
                assertEquals("New bio here", captor.getValue().getBio()); // updated
        }

        // ===== FOLLOW =====

        @Test
        void follow_createsRelationshipAndUpdatesCounters() {
                when(followRepository.existsByFollowerIdAndFollowingId(userAId, userBId)).thenReturn(false);
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(userRepository.findById(userBId)).thenReturn(Optional.of(userB));

                userProfileService.follow(userAId, userBId);

                verify(followRepository).save(any(Follow.class));

                // Verify counters were incremented
                ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
                verify(userRepository, atLeast(2)).save(captor.capture());

                List<AppUser> savedUsers = captor.getAllValues();
                // Find userA (follower) — followingCount should be 1
                AppUser savedFollower = savedUsers.stream()
                                .filter(u -> u.getId().equals(userAId)).findFirst().orElseThrow();
                assertEquals(1, savedFollower.getFollowingCount());

                // Find userB (following) — followerCount should be 1
                AppUser savedFollowing = savedUsers.stream()
                                .filter(u -> u.getId().equals(userBId)).findFirst().orElseThrow();
                assertEquals(1, savedFollowing.getFollowerCount());
        }

        @Test
        void follow_throwsWhenFollowingSelf() {
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> userProfileService.follow(userAId, userAId));
                assertEquals("Cannot follow yourself", ex.getMessage());
        }

        @Test
        void follow_throwsWhenAlreadyFollowing() {
                when(followRepository.existsByFollowerIdAndFollowingId(userAId, userBId)).thenReturn(true);

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> userProfileService.follow(userAId, userBId));
                assertEquals("Already following", ex.getMessage());
        }

        @Test
        void follow_throwsWhenTargetUserNotFound() {
                when(followRepository.existsByFollowerIdAndFollowingId(userAId, userBId)).thenReturn(false);
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(userRepository.findById(userBId)).thenReturn(Optional.empty());

                assertThrows(RuntimeException.class, () -> userProfileService.follow(userAId, userBId));
        }

        // ===== UNFOLLOW =====

        @Test
        void unfollow_deletesRelationshipAndDecrementsCounters() {
                userA.setFollowingCount(3);
                userB.setFollowerCount(5);

                Follow existingFollow = Follow.builder()
                                .id(UUID.randomUUID()).followerId(userAId).followingId(userBId).build();

                when(followRepository.findByFollowerIdAndFollowingId(userAId, userBId))
                                .thenReturn(Optional.of(existingFollow));
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(userRepository.findById(userBId)).thenReturn(Optional.of(userB));

                userProfileService.unfollow(userAId, userBId);

                verify(followRepository).delete(existingFollow);

                ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
                verify(userRepository, atLeast(2)).save(captor.capture());

                AppUser savedFollower = captor.getAllValues().stream()
                                .filter(u -> u.getId().equals(userAId)).findFirst().orElseThrow();
                assertEquals(2, savedFollower.getFollowingCount()); // 3 -> 2

                AppUser savedFollowing = captor.getAllValues().stream()
                                .filter(u -> u.getId().equals(userBId)).findFirst().orElseThrow();
                assertEquals(4, savedFollowing.getFollowerCount()); // 5 -> 4
        }

        @Test
        void unfollow_countersNeverGoNegative() {
                userA.setFollowingCount(0);
                userB.setFollowerCount(0);

                Follow existingFollow = Follow.builder()
                                .id(UUID.randomUUID()).followerId(userAId).followingId(userBId).build();

                when(followRepository.findByFollowerIdAndFollowingId(userAId, userBId))
                                .thenReturn(Optional.of(existingFollow));
                when(userRepository.findById(userAId)).thenReturn(Optional.of(userA));
                when(userRepository.findById(userBId)).thenReturn(Optional.of(userB));

                userProfileService.unfollow(userAId, userBId);

                ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
                verify(userRepository, atLeast(2)).save(captor.capture());

                // Counters should remain 0, not go to -1
                captor.getAllValues().forEach(u -> {
                        assertTrue(u.getFollowerCount() >= 0, "followerCount must not be negative");
                        assertTrue(u.getFollowingCount() >= 0, "followingCount must not be negative");
                });
        }

        @Test
        void unfollow_throwsWhenNotFollowing() {
                when(followRepository.findByFollowerIdAndFollowingId(userAId, userBId))
                                .thenReturn(Optional.empty());

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> userProfileService.unfollow(userAId, userBId));
                assertEquals("Not following this user", ex.getMessage());
        }

        // ===== FOLLOWERS / FOLLOWING LISTS =====

        @Test
        void getFollowers_returnsListOfProfiles() {
                Follow f = Follow.builder().followerId(userBId).followingId(userAId).build();
                Pageable pageable = PageRequest.of(0, 20);

                when(followRepository.findByFollowingId(eq(userAId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(f)));
                when(userRepository.findById(userBId)).thenReturn(Optional.of(userB));
                when(portfolioRepository.findByOwnerId(userBId.toString())).thenReturn(List.of());

                Page<UserProfileResponse> followers = userProfileService.getFollowers(userAId, null, pageable);

                assertEquals(1, followers.getTotalElements());
                assertEquals("bob", followers.getContent().get(0).getUsername());
        }

        @Test
        void getFollowing_returnsListOfProfiles() {
                Follow f = Follow.builder().followerId(userAId).followingId(userBId).build();
                Pageable pageable = PageRequest.of(0, 20);

                when(followRepository.findByFollowerId(eq(userAId), any(Pageable.class)))
                                .thenReturn(new PageImpl<>(List.of(f)));
                when(userRepository.findById(userBId)).thenReturn(Optional.of(userB));
                when(portfolioRepository.findByOwnerId(userBId.toString())).thenReturn(List.of());

                Page<UserProfileResponse> following = userProfileService.getFollowing(userAId, null, pageable);

                assertEquals(1, following.getTotalElements());
                assertEquals("bob", following.getContent().get(0).getUsername());
        }
}
