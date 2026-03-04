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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
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
}
