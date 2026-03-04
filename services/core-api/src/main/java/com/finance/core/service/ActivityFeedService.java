package com.finance.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.finance.core.domain.*;
import com.finance.core.repository.ActivityEventRepository;
import com.finance.core.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityFeedService {

    private final ActivityEventRepository eventRepository;
    private final FollowRepository followRepository;
    private final CacheService cacheService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String GLOBAL_FEED_KEY = "feed:global";
    private static final String USER_FEED_KEY_PREFIX = "feed:user:";
    private static final int MIN_LOCAL_CACHE_ENTRIES = 16;
    private static final int MIN_LOCAL_LOCK_STRIPES = 1;
    private static final int MIN_FOLLOWER_INVALIDATION_BATCH_SIZE = 50;
    private static final int DEFAULT_FOLLOWER_INVALIDATION_BATCH_SIZE = 500;
    private static final int DEFAULT_LOCAL_LOCK_STRIPES = 64;

    private final ConcurrentHashMap<String, LocalFeedCacheEntry> localFeedCache = new ConcurrentHashMap<>();
    private volatile Object[] localFeedLocks = createLockStripes(DEFAULT_LOCAL_LOCK_STRIPES);
    private Duration redisFeedTtl = Duration.ofSeconds(15);
    private Duration localFeedTtl = Duration.ofSeconds(20);
    private int localFeedMaxEntries = 512;
    private int followerInvalidationBatchSize = DEFAULT_FOLLOWER_INVALIDATION_BATCH_SIZE;
    private boolean invalidateGlobalCacheOnPublish = true;
    private boolean invalidateFollowerCachesOnPublish = false;
    private boolean broadcastToFollowersOnPublish = false;

    @Value("${app.feed.cache.redis-ttl:PT15S}")
    public void setRedisFeedTtl(Duration redisFeedTtl) {
        this.redisFeedTtl = redisFeedTtl == null ? Duration.ofSeconds(15) : redisFeedTtl;
    }

    @Value("${app.feed.cache.local-ttl:PT20S}")
    public void setLocalFeedTtl(Duration localFeedTtl) {
        this.localFeedTtl = localFeedTtl == null ? Duration.ofSeconds(20) : localFeedTtl;
    }

    @Value("${app.feed.cache.local-max-entries:512}")
    public void setLocalFeedMaxEntries(int localFeedMaxEntries) {
        this.localFeedMaxEntries = Math.max(MIN_LOCAL_CACHE_ENTRIES, localFeedMaxEntries);
    }

    @Value("${app.feed.cache.local-lock-stripes:64}")
    public void setLocalFeedLockStripes(int localFeedLockStripes) {
        this.localFeedLocks = createLockStripes(Math.max(MIN_LOCAL_LOCK_STRIPES, localFeedLockStripes));
    }

    @Value("${app.feed.cache.follower-invalidation-batch-size:500}")
    public void setFollowerInvalidationBatchSize(int followerInvalidationBatchSize) {
        this.followerInvalidationBatchSize = Math.max(MIN_FOLLOWER_INVALIDATION_BATCH_SIZE, followerInvalidationBatchSize);
    }

    @Value("${app.feed.cache.invalidate-global-on-publish:true}")
    public void setInvalidateGlobalCacheOnPublish(boolean invalidateGlobalCacheOnPublish) {
        this.invalidateGlobalCacheOnPublish = invalidateGlobalCacheOnPublish;
    }

    @Value("${app.feed.cache.invalidate-followers-on-publish:false}")
    public void setInvalidateFollowerCachesOnPublish(boolean invalidateFollowerCachesOnPublish) {
        this.invalidateFollowerCachesOnPublish = invalidateFollowerCachesOnPublish;
    }

    @Value("${app.feed.realtime.broadcast-followers-on-publish:false}")
    public void setBroadcastToFollowersOnPublish(boolean broadcastToFollowersOnPublish) {
        this.broadcastToFollowersOnPublish = broadcastToFollowersOnPublish;
    }

    /**
     * Publish an activity event.
     * Invalidates relevant caches to ensure feed freshness.
     */
    public ActivityEvent publish(UUID actorId, String actorUsername,
            ActivityEvent.EventType eventType,
            ActivityEvent.TargetType targetType,
            UUID targetId, String targetLabel) {
        ActivityEvent event = ActivityEvent.builder()
                .actorId(actorId)
                .actorUsername(actorUsername)
                .eventType(eventType)
                .targetType(targetType)
                .targetId(targetId)
                .targetLabel(targetLabel)
                .build();

        event = eventRepository.save(event);
        log.info("Activity event: {} {} {} ({})", actorUsername, eventType, targetLabel, targetId);

        // Clear local near-cache first to avoid stale reads in this instance.
        localFeedCache.clear();

        // Invalidate all global feed pages when eager invalidation is enabled.
        if (invalidateGlobalCacheOnPublish) {
            cacheService.deletePattern(GLOBAL_FEED_KEY + ":*");
        }

        // For scale, personalized follower invalidation/broadcast is optional and disabled by default.
        if (invalidateFollowerCachesOnPublish || broadcastToFollowersOnPublish) {
            try {
                int page = 0;
                Page<Follow> followerPage;
                do {
                    followerPage = followRepository.findByFollowingId(actorId,
                            PageRequest.of(page, followerInvalidationBatchSize));
                    for (Follow f : followerPage.getContent()) {
                        if (invalidateFollowerCachesOnPublish) {
                            cacheService.deletePattern(USER_FEED_KEY_PREFIX + f.getFollowerId() + ":*");
                        }
                        if (broadcastToFollowersOnPublish) {
                            messagingTemplate.convertAndSend("/topic/feed/" + f.getFollowerId(), event);
                        }
                    }
                    page++;
                } while (followerPage.hasNext());
            } catch (Exception e) {
                log.debug("Follower feed update on publish skipped: {}", e.getMessage());
            }
        }

        // Global broadcast
        messagingTemplate.convertAndSend("/topic/feed/global", event);

        return event;
    }

    /**
     * Personalized feed: events from users you follow.
     * Uses Redis cache with short TTL for performance.
     */
    public Page<ActivityEvent> getPersonalizedFeed(UUID userId, Pageable pageable) {
        String cacheKey = buildUserFeedCacheKey(userId, pageable);

        Optional<Page<ActivityEvent>> localCacheHit = getLocalPage(cacheKey, pageable);
        if (localCacheHit.isPresent()) {
            return localCacheHit.get();
        }

        return withCacheKeyLock(cacheKey, () -> {
            Optional<Page<ActivityEvent>> inLockLocalHit = getLocalPage(cacheKey, pageable);
            if (inLockLocalHit.isPresent()) {
                return inLockLocalHit.get();
            }

            // Try cache first
            Optional<CachedFeedPage> cached = cacheService.get(cacheKey, new TypeReference<CachedFeedPage>() {
            });
            if (cached.isPresent()) {
                try {
                    CachedFeedPage cachedPage = cached.get();
                    putLocalPage(cacheKey, cachedPage);
                    return toPage(cachedPage, pageable);
                } catch (Exception e) {
                    // Cache deserialization failed, fall through to DB
                }
            }

            Page<ActivityEvent> result = eventRepository.findPersonalizedFeedByFollowerId(userId, pageable);
            CachedFeedPage dbPage = new CachedFeedPage(result.getContent(), result.getTotalElements());
            putLocalPage(cacheKey, dbPage);

            // Cache result
            try {
                cacheService.set(cacheKey, dbPage, redisFeedTtl);
            } catch (Exception e) {
                log.debug("Failed to cache personalized feed: {}", e.getMessage());
            }

            return result;
        });
    }

    /**
     * Global feed: all activity, newest first.
     * Cached with short TTL for high-traffic reads.
     */
    public Page<ActivityEvent> getGlobalFeed(Pageable pageable) {
        String cacheKey = buildGlobalFeedCacheKey(pageable);

        Optional<Page<ActivityEvent>> localCacheHit = getLocalPage(cacheKey, pageable);
        if (localCacheHit.isPresent()) {
            return localCacheHit.get();
        }

        return withCacheKeyLock(cacheKey, () -> {
            Optional<Page<ActivityEvent>> inLockLocalHit = getLocalPage(cacheKey, pageable);
            if (inLockLocalHit.isPresent()) {
                return inLockLocalHit.get();
            }

            // Try cache
            Optional<CachedFeedPage> cached = cacheService.get(cacheKey, new TypeReference<CachedFeedPage>() {
            });
            if (cached.isPresent()) {
                try {
                    CachedFeedPage cachedPage = cached.get();
                    putLocalPage(cacheKey, cachedPage);
                    return toPage(cachedPage, pageable);
                } catch (Exception e) {
                    // Fall through
                }
            }

            Page<ActivityEvent> result = eventRepository.findAllByOrderByCreatedAtDesc(pageable);
            CachedFeedPage dbPage = new CachedFeedPage(result.getContent(), result.getTotalElements());
            putLocalPage(cacheKey, dbPage);

            // Cache
            try {
                cacheService.set(cacheKey, dbPage, redisFeedTtl);
            } catch (Exception e) {
                log.debug("Failed to cache global feed: {}", e.getMessage());
            }

            return result;
        });
    }

    /**
     * Single user's activity history.
     */
    public Page<ActivityEvent> getUserActivity(UUID userId, Pageable pageable) {
        return eventRepository.findByActorIdOrderByCreatedAtDesc(userId, pageable);
    }

    private String buildGlobalFeedCacheKey(Pageable pageable) {
        return GLOBAL_FEED_KEY + ":" + buildPageCacheSuffix(pageable);
    }

    private String buildUserFeedCacheKey(UUID userId, Pageable pageable) {
        return USER_FEED_KEY_PREFIX + userId + ":" + buildPageCacheSuffix(pageable);
    }

    private String buildPageCacheSuffix(Pageable pageable) {
        String sortPart = pageable.getSort().isSorted() ? pageable.getSort().toString() : "unsorted";
        return "p" + pageable.getPageNumber() + ":s" + pageable.getPageSize() + ":sort:" + sortPart;
    }

    private void putLocalPage(String cacheKey, CachedFeedPage page) {
        if (localFeedCache.size() >= localFeedMaxEntries) {
            localFeedCache.clear();
        }
        localFeedCache.put(cacheKey, new LocalFeedCacheEntry(page, System.nanoTime() + localFeedTtl.toNanos()));
    }

    private Optional<Page<ActivityEvent>> getLocalPage(String cacheKey, Pageable pageable) {
        LocalFeedCacheEntry entry = localFeedCache.get(cacheKey);
        if (entry == null) {
            return Optional.empty();
        }
        if (System.nanoTime() > entry.expiresAtNanos()) {
            localFeedCache.remove(cacheKey, entry);
            return Optional.empty();
        }
        return Optional.of(toPage(entry.cachedPage(), pageable));
    }

    private Page<ActivityEvent> toPage(CachedFeedPage cachedPage, Pageable pageable) {
        return new PageImpl<>(cachedPage.getContent(), pageable, cachedPage.getTotalElements());
    }

    private <T> T withCacheKeyLock(String cacheKey, Supplier<T> supplier) {
        Object[] locks = localFeedLocks;
        Object lock = locks[Math.floorMod(cacheKey.hashCode(), locks.length)];
        synchronized (lock) {
            return supplier.get();
        }
    }

    private Object[] createLockStripes(int stripeCount) {
        return IntStream.range(0, stripeCount)
                .mapToObj(i -> new Object())
                .toArray();
    }

    private record LocalFeedCacheEntry(CachedFeedPage cachedPage, long expiresAtNanos) {
    }

    public static class CachedFeedPage {
        private List<ActivityEvent> content;
        private long totalElements;

        public CachedFeedPage() {
        }

        public CachedFeedPage(List<ActivityEvent> content, long totalElements) {
            this.content = content == null ? Collections.emptyList() : List.copyOf(content);
            this.totalElements = totalElements;
        }

        public List<ActivityEvent> getContent() {
            return content;
        }

        public void setContent(List<ActivityEvent> content) {
            this.content = content == null ? Collections.emptyList() : List.copyOf(content);
        }

        public long getTotalElements() {
            return totalElements;
        }

        public void setTotalElements(long totalElements) {
            this.totalElements = totalElements;
        }
    }
}
