package com.finance.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    private CacheService cacheService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        cacheService = new CacheService(redisTemplate, objectMapper, meterRegistry);
    }

    @Test
    void deletePattern_shouldUseScanCallback_notKeys() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        cacheService.deletePattern("feed:*");

        verify(redisTemplate).execute(any(RedisCallback.class));
        verify(redisTemplate, never()).keys(anyString());
    }

    @Test
    void incrementWithTtl_firstIncrement_setsExpiry() {
        String key = "rate:ip:1";
        Duration ttl = Duration.ofSeconds(30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(1L);

        cacheService.incrementWithTtl(key, ttl);

        verify(redisTemplate).expire(eq(key), eq(ttl.toSeconds()), eq(TimeUnit.SECONDS));
    }

    @Test
    void incrementWithTtl_subsequentIncrement_doesNotResetExpiry() {
        String key = "rate:ip:2";
        Duration ttl = Duration.ofSeconds(30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(5L);

        cacheService.incrementWithTtl(key, ttl);

        verify(redisTemplate, never()).expire(eq(key), eq(ttl.toSeconds()), eq(TimeUnit.SECONDS));
    }

    @Test
    void incrementWithTtl_whenExpireFails_shouldReturnCountAndRecordTtlError() {
        String key = "feed:user-version:1";
        Duration ttl = Duration.ofMinutes(5);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenReturn(1L);
        doThrow(new RuntimeException("expire-down")).when(redisTemplate)
                .expire(eq(key), eq(ttl.toSeconds()), eq(TimeUnit.SECONDS));

        Long result = cacheService.incrementWithTtl(key, ttl);

        assertEquals(1L, result);
        Counter errorCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "increment_with_ttl", "result", "ttl_error")
                .counter();
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void incrementWithTtl_whenIncrementFails_shouldReturnNullAndRecordError() {
        String key = "feed:user-version:2";
        Duration ttl = Duration.ofMinutes(5);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(key)).thenThrow(new RuntimeException("increment-down"));

        Long result = cacheService.incrementWithTtl(key, ttl);

        assertNull(result);
        Counter errorCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "increment_with_ttl", "result", "error")
                .counter();
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void get_shouldRecordHitMetric_whenValueExists() {
        String key = "feed:key";
        Object payload = new Object();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(payload);
        when(objectMapper.convertValue(payload, String.class)).thenReturn("ok");

        cacheService.get(key, String.class);

        Counter hitCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "get", "result", "hit")
                .counter();
        Timer latencyTimer = meterRegistry.find("app.redis.command.latency")
                .tags("command", "get", "result", "hit")
                .timer();

        assertNotNull(hitCounter);
        assertNotNull(latencyTimer);
        assertEquals(1.0, hitCounter.count());
        assertEquals(1, latencyTimer.count());
    }

    @Test
    void get_shouldRecordMissMetric_whenValueMissing() {
        String key = "feed:missing";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        cacheService.get(key, String.class);

        Counter missCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "get", "result", "miss")
                .counter();
        assertNotNull(missCounter);
        assertEquals(1.0, missCounter.count());
    }

    @Test
    void getTyped_shouldReturnEmptyAndRecordError_whenRedisThrows() {
        String key = "feed:typed";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenThrow(new RuntimeException("typed-down"));

        var result = cacheService.get(key, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
        });

        assertTrue(result.isEmpty());
        Counter errorCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "get_typed", "result", "error")
                .counter();
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void zRevRangeWithScores_shouldReturnEmptySetAndRecordError_whenRedisThrows() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores("leaderboard_portfolios:ALL", 0, 9))
                .thenThrow(new RuntimeException("zset-down"));

        Set<ZSetOperations.TypedTuple<Object>> result = cacheService.zRevRangeWithScores("leaderboard_portfolios:ALL", 0, 9);

        assertTrue(result.isEmpty());
        Counter errorCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "zrevrange", "result", "error")
                .counter();
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }

    @Test
    void zCard_shouldReturnZeroAndRecordError_whenRedisThrows() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.zCard("leaderboard_accounts:ALL"))
                .thenThrow(new RuntimeException("zcard-down"));

        Long result = cacheService.zCard("leaderboard_accounts:ALL");

        assertEquals(0L, result);
        Counter errorCounter = meterRegistry.find("app.redis.command.calls")
                .tags("command", "zcard", "result", "error")
                .counter();
        assertNotNull(errorCounter);
        assertEquals(1.0, errorCounter.count());
    }
}
