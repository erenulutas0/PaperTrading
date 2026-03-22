package com.finance.core.service;

import com.finance.core.domain.ActivityEvent;
import com.finance.core.domain.Follow;
import com.finance.core.repository.ActivityEventRepository;
import com.finance.core.repository.FollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityFeedServiceTest {

    @Mock
    private ActivityEventRepository eventRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private CacheService cacheService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ActivityFeedService feedService;

    @Test
    void publish_savesEventAndReturnsIt() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        feedService.setInvalidateFollowerCachesOnPublish(true);
        feedService.setFollowerFeedVersionTtl(Duration.ofMinutes(5));
        when(followRepository.findByFollowingId(eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(Follow.builder().followerId(followerId).followingId(actorId).build())));
        when(cacheService.incrementWithTtl("feed:user-version:" + followerId, Duration.ofMinutes(5))).thenReturn(1L);

        when(eventRepository.save(any())).thenAnswer(inv -> {
            ActivityEvent e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        ActivityEvent event = feedService.publish(
                actorId, "trader1",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                targetId, "BTC Analysis");

        assertNotNull(event.getId());
        assertEquals(actorId, event.getActorId());
        assertEquals("trader1", event.getActorUsername());
        assertEquals(ActivityEvent.EventType.POST_CREATED, event.getEventType());
        assertEquals("BTC Analysis", event.getTargetLabel());
        verify(eventRepository).save(any(ActivityEvent.class));
        verify(cacheService).deletePattern("feed:global:*");
        verify(cacheService).incrementWithTtl("feed:user-version:" + followerId, Duration.ofMinutes(5));
        verify(cacheService, never()).deletePattern("feed:user:" + followerId + ":*");
    }

    @Test
    void publish_shouldIterateFollowersWithPagination() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID followerA = UUID.randomUUID();
        UUID followerB = UUID.randomUUID();
        feedService.setInvalidateFollowerCachesOnPublish(true);
        when(cacheService.incrementWithTtl(startsWith("feed:user-version:"), any(Duration.class))).thenReturn(1L);

        Page<Follow> first = new PageImpl<>(
                List.of(Follow.builder().followerId(followerA).followingId(actorId).build()),
                PageRequest.of(0, 500),
                501);
        Page<Follow> second = new PageImpl<>(
                List.of(Follow.builder().followerId(followerB).followingId(actorId).build()),
                PageRequest.of(1, 500),
                501);

        when(followRepository.findByFollowingId(eq(actorId), eq(PageRequest.of(0, 500)))).thenReturn(first);
        when(followRepository.findByFollowingId(eq(actorId), eq(PageRequest.of(1, 500)))).thenReturn(second);
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        feedService.publish(
                actorId, "trader1",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                targetId, "BTC Analysis");

        verify(followRepository).findByFollowingId(eq(actorId), eq(PageRequest.of(0, 500)));
        verify(followRepository).findByFollowingId(eq(actorId), eq(PageRequest.of(1, 500)));
        verify(cacheService).incrementWithTtl(eq("feed:user-version:" + followerA), any(Duration.class));
        verify(cacheService).incrementWithTtl(eq("feed:user-version:" + followerB), any(Duration.class));
    }

    @Test
    void publish_defaultMode_shouldSkipFollowerScanAndFollowerInvalidation() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        feedService.publish(
                actorId, "trader1",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                targetId, "BTC Analysis");

        verify(followRepository, never()).findByFollowingId(any(UUID.class), any(Pageable.class));
        verify(cacheService, never()).deletePattern(startsWith("feed:user:"));
        verify(cacheService).deletePattern("feed:global:*");
    }

    @Test
    void publish_shouldContinueWhenGlobalCacheInvalidationThrows() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("redis-down")).when(cacheService).deletePattern("feed:global:*");

        ActivityEvent event = feedService.publish(
                actorId, "trader1",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                targetId, "BTC Analysis");

        assertNotNull(event);
        verify(eventRepository).save(any(ActivityEvent.class));
        verify(messagingTemplate).convertAndSend("/topic/feed/global", event);
    }

    @Test
    void getPersonalizedFeed_returnsEventsFromFollowedUsers() {
        UUID userId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(followedId)
                .eventType(ActivityEvent.EventType.POST_CREATED)
                .targetType(ActivityEvent.TargetType.POST)
                .targetId(UUID.randomUUID())
                .build();
        when(cacheService.get("feed:user-version:" + userId, Long.class)).thenReturn(Optional.of(3L));
        when(eventRepository.findPersonalizedFeedByFollowerId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ActivityEvent> feed = feedService.getPersonalizedFeed(userId, pageable);

        assertEquals(1, feed.getTotalElements());
        assertEquals(followedId, feed.getContent().get(0).getActorId());
        verify(cacheService).get(startsWith("feed:user:" + userId + ":v3:"), any(com.fasterxml.jackson.core.type.TypeReference.class));
        verify(eventRepository).findPersonalizedFeedByFollowerId(userId, pageable);
        verify(followRepository, never()).findByFollowerId(any(UUID.class));
    }

    @Test
    void getPersonalizedFeed_shouldFallbackWhenVersionLookupThrows() {
        UUID userId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(followedId)
                .eventType(ActivityEvent.EventType.POST_CREATED)
                .targetType(ActivityEvent.TargetType.POST)
                .targetId(UUID.randomUUID())
                .build();
        when(cacheService.get("feed:user-version:" + userId, Long.class)).thenThrow(new RuntimeException("redis-down"));
        when(cacheService.get(startsWith("feed:user:" + userId + ":v0:"), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findPersonalizedFeedByFollowerId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ActivityEvent> feed = feedService.getPersonalizedFeed(userId, pageable);

        assertEquals(1, feed.getTotalElements());
        verify(eventRepository).findPersonalizedFeedByFollowerId(userId, pageable);
    }

    @Test
    void getPersonalizedFeed_emptyWhenNoFollows() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        when(cacheService.get("feed:user-version:" + userId, Long.class)).thenReturn(Optional.empty());
        when(eventRepository.findPersonalizedFeedByFollowerId(userId, pageable))
                .thenReturn(Page.empty(pageable));

        Page<ActivityEvent> feed = feedService.getPersonalizedFeed(userId, pageable);

        assertTrue(feed.isEmpty());
        verify(eventRepository).findPersonalizedFeedByFollowerId(userId, pageable);
        verify(followRepository, never()).findByFollowerId(any(UUID.class));
    }

    @Test
    void publish_shouldFallbackToPatternDelete_whenVersionedFollowerKeysDisabled() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        feedService.setInvalidateFollowerCachesOnPublish(true);
        feedService.setVersionFollowerFeedKeysOnPublish(false);

        when(followRepository.findByFollowingId(eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(Follow.builder().followerId(followerId).followingId(actorId).build())));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        feedService.publish(
                actorId, "trader1",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                targetId, "BTC Analysis");

        verify(cacheService).deletePattern("feed:user:" + followerId + ":*");
        verify(cacheService, never()).incrementWithTtl(eq("feed:user-version:" + followerId), any(Duration.class));
    }

    @Test
    void publish_shouldFallbackToPatternDelete_whenVersionIncrementThrows() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        feedService.setInvalidateFollowerCachesOnPublish(true);

        when(followRepository.findByFollowingId(eq(actorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(Follow.builder().followerId(followerId).followingId(actorId).build())));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cacheService.incrementWithTtl(eq("feed:user-version:" + followerId), any(Duration.class)))
                .thenThrow(new RuntimeException("redis-down"));

        feedService.publish(
                actorId, "trader1",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                targetId, "BTC Analysis");

        verify(cacheService).deletePattern("feed:user:" + followerId + ":*");
    }

    @Test
    void getGlobalFeed_returnsAllEvents() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .eventType(ActivityEvent.EventType.FOLLOW)
                .targetType(ActivityEvent.TargetType.USER)
                .targetId(UUID.randomUUID())
                .build();

        when(eventRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ActivityEvent> feed = feedService.getGlobalFeed(pageable);

        assertEquals(1, feed.getTotalElements());
    }

    @Test
    void getGlobalFeed_cacheHit_returnsCachedTotalElements() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .eventType(ActivityEvent.EventType.FOLLOW)
                .targetType(ActivityEvent.TargetType.USER)
                .targetId(UUID.randomUUID())
                .build();
        ActivityFeedService.CachedFeedPage cachedPage = new ActivityFeedService.CachedFeedPage(List.of(event), 42L);

        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.of(cachedPage));

        Page<ActivityEvent> result = feedService.getGlobalFeed(pageable);

        assertEquals(42L, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        verify(eventRepository, never()).findAllByOrderByCreatedAtDesc(any());
    }

    @Test
    void getGlobalFeed_shouldFallbackToRepositoryWhenCacheReadThrows() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .eventType(ActivityEvent.EventType.FOLLOW)
                .targetType(ActivityEvent.TargetType.USER)
                .targetId(UUID.randomUUID())
                .build();

        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new RuntimeException("redis-down"));
        when(eventRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ActivityEvent> result = feedService.getGlobalFeed(pageable);

        assertEquals(1L, result.getTotalElements());
        verify(eventRepository).findAllByOrderByCreatedAtDesc(pageable);
    }

    @Test
    void getGlobalFeed_secondRead_shouldUseLocalNearCache() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .eventType(ActivityEvent.EventType.FOLLOW)
                .targetType(ActivityEvent.TargetType.USER)
                .targetId(UUID.randomUUID())
                .build();

        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ActivityEvent> first = feedService.getGlobalFeed(pageable);
        Page<ActivityEvent> second = feedService.getGlobalFeed(pageable);

        assertEquals(1, first.getTotalElements());
        assertEquals(1, second.getTotalElements());
        verify(cacheService, times(1)).get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class));
        verify(eventRepository, times(1)).findAllByOrderByCreatedAtDesc(pageable);
    }

    @Test
    void publish_shouldClearLocalNearCache() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .eventType(ActivityEvent.EventType.FOLLOW)
                .targetType(ActivityEvent.TargetType.USER)
                .targetId(UUID.randomUUID())
                .build();

        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(event)));
        when(eventRepository.save(any(ActivityEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        feedService.getGlobalFeed(pageable);
        feedService.getGlobalFeed(pageable); // local near-cache hit

        feedService.publish(
                UUID.randomUUID(),
                "actor",
                ActivityEvent.EventType.POST_CREATED,
                ActivityEvent.TargetType.POST,
                UUID.randomUUID(),
                "post");

        feedService.getGlobalFeed(pageable); // should miss local cache after publish

        verify(eventRepository, times(2)).findAllByOrderByCreatedAtDesc(pageable);
    }

    @Test
    void configSetters_shouldClampInvalidValues_andKeepServiceOperational() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivityEvent event = ActivityEvent.builder()
                .id(UUID.randomUUID())
                .actorId(UUID.randomUUID())
                .eventType(ActivityEvent.EventType.FOLLOW)
                .targetType(ActivityEvent.TargetType.USER)
                .targetId(UUID.randomUUID())
                .build();

        feedService.setLocalFeedMaxEntries(0);
        feedService.setLocalFeedLockStripes(0);

        when(cacheService.get(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenReturn(Optional.empty());
        when(eventRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<ActivityEvent> first = feedService.getGlobalFeed(pageable);
        Page<ActivityEvent> second = feedService.getGlobalFeed(pageable);

        assertEquals(1, first.getTotalElements());
        assertEquals(1, second.getTotalElements());
        verify(eventRepository, times(1)).findAllByOrderByCreatedAtDesc(pageable);
    }
}
