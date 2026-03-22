package com.finance.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Centralized cache service wrapping Redis operations.
 * If Redis is unavailable, operations silently fail (cache-aside pattern).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private static final String REDIS_COMMAND_CALLS_METRIC = "app.redis.command.calls";
    private static final String REDIS_COMMAND_LATENCY_METRIC = "app.redis.command.latency";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // ==================== STRING operations ====================

    /** Set a value with TTL */
    public void set(String key, Object value, Duration ttl) {
        long start = System.nanoTime();
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            recordCommand("set", "success", start);
        } catch (Exception e) {
            recordCommand("set", "error", start);
            log.warn("Redis SET failed for key {}: {}", key, e.getMessage());
        }
    }

    /** Get a value */
    public <T> Optional<T> get(String key, Class<T> type) {
        long start = System.nanoTime();
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                recordCommand("get", "miss", start);
                return Optional.empty();
            }
            recordCommand("get", "hit", start);
            return Optional.of(objectMapper.convertValue(value, type));
        } catch (Exception e) {
            recordCommand("get", "error", start);
            log.warn("Redis GET failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** Get a list value (with TypeReference for generics) */
    public <T> Optional<T> get(String key, TypeReference<T> typeRef) {
        long start = System.nanoTime();
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                recordCommand("get_typed", "miss", start);
                return Optional.empty();
            }
            recordCommand("get_typed", "hit", start);
            return Optional.of(objectMapper.convertValue(value, typeRef));
        } catch (Exception e) {
            recordCommand("get_typed", "error", start);
            log.warn("Redis GET failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /** Delete a key */
    public void delete(String key) {
        long start = System.nanoTime();
        try {
            redisTemplate.delete(key);
            recordCommand("delete", "success", start);
        } catch (Exception e) {
            recordCommand("delete", "error", start);
            log.warn("Redis DELETE failed for key {}: {}", key, e.getMessage());
        }
    }

    /** Delete keys matching pattern */
    public void deletePattern(String pattern) {
        long start = System.nanoTime();
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(500)
                        .build();

                List<byte[]> batch = new ArrayList<>(500);
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= 500) {
                            connection.del(batch.toArray(new byte[0][]));
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        connection.del(batch.toArray(new byte[0][]));
                    }
                }
                return null;
            });
            recordCommand("delete_pattern", "success", start);
        } catch (Exception e) {
            recordCommand("delete_pattern", "error", start);
            log.warn("Redis DELETE pattern failed for {}: {}", pattern, e.getMessage());
        }
    }

    // ==================== SORTED SET (leaderboard) ====================

    /** Add entry to sorted set */
    public void zAdd(String key, String member, double score) {
        long start = System.nanoTime();
        try {
            redisTemplate.opsForZSet().add(key, member, score);
            recordCommand("zadd", "success", start);
        } catch (Exception e) {
            recordCommand("zadd", "error", start);
            log.warn("Redis ZADD failed for key {}: {}", key, e.getMessage());
        }
    }

    /** Get top N from sorted set (descending) */
    public Set<ZSetOperations.TypedTuple<Object>> zRevRangeWithScores(String key, long start, long end) {
        long startedAt = System.nanoTime();
        try {
            Set<ZSetOperations.TypedTuple<Object>> result = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
            recordCommand("zrevrange", "success", startedAt);
            return result;
        } catch (Exception e) {
            recordCommand("zrevrange", "error", startedAt);
            log.warn("Redis ZREVRANGE failed for key {}: {}", key, e.getMessage());
            return Set.of();
        }
    }

    /** Get top N from sorted set (ascending) */
    public Set<ZSetOperations.TypedTuple<Object>> zRangeWithScores(String key, long start, long end) {
        long startedAt = System.nanoTime();
        try {
            Set<ZSetOperations.TypedTuple<Object>> result = redisTemplate.opsForZSet().rangeWithScores(key, start, end);
            recordCommand("zrange", "success", startedAt);
            return result;
        } catch (Exception e) {
            recordCommand("zrange", "error", startedAt);
            log.warn("Redis ZRANGE failed for key {}: {}", key, e.getMessage());
            return Set.of();
        }
    }

    /** Get size of sorted set */
    public Long zCard(String key) {
        long start = System.nanoTime();
        try {
            Long size = redisTemplate.opsForZSet().zCard(key);
            recordCommand("zcard", "success", start);
            return size;
        } catch (Exception e) {
            recordCommand("zcard", "error", start);
            log.warn("Redis ZCARD failed for key {}: {}", key, e.getMessage());
            return 0L;
        }
    }

    /** Set TTL on an existing key */
    public void expire(String key, Duration ttl) {
        long start = System.nanoTime();
        try {
            redisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
            recordCommand("expire", "success", start);
        } catch (Exception e) {
            recordCommand("expire", "error", start);
            log.warn("Redis EXPIRE failed for key {}: {}", key, e.getMessage());
        }
    }

    /** Check if key exists */
    public boolean exists(String key) {
        long start = System.nanoTime();
        try {
            boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(key));
            recordCommand("exists", exists ? "hit" : "miss", start);
            return exists;
        } catch (Exception e) {
            recordCommand("exists", "error", start);
            return false;
        }
    }

    // ==================== INCREMENT (counters / rate limit) ====================

    /** Increment a counter */
    public Long increment(String key) {
        long start = System.nanoTime();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            recordCommand("increment", "success", start);
            return count;
        } catch (Exception e) {
            recordCommand("increment", "error", start);
            log.warn("Redis INCREMENT failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    /** Increment a counter with TTL (for rate limiting) */
    public Long incrementWithTtl(String key, Duration ttl) {
        long start = System.nanoTime();
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                try {
                    redisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
                } catch (Exception e) {
                    recordCommand("increment_with_ttl", "ttl_error", start);
                    log.warn("Redis EXPIRE after INCREMENT failed for key {}: {}", key, e.getMessage());
                    return count;
                }
            }
            recordCommand("increment_with_ttl", "success", start);
            return count;
        } catch (Exception e) {
            recordCommand("increment_with_ttl", "error", start);
            log.warn("Redis INCREMENT failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void recordCommand(String command, String result, long startNanos) {
        meterRegistry.counter(REDIS_COMMAND_CALLS_METRIC, "command", command, "result", result).increment();
        Timer.builder(REDIS_COMMAND_LATENCY_METRIC)
                .description("Latency of Redis commands issued by CacheService")
                .tags("command", command, "result", result)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }
}
