package com.finance.core.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void resolveBucket_loopbackShouldReuseSingleBypassBucket() {
        Bucket first = filter.resolveBucket("127.0.0.1");
        Bucket second = filter.resolveBucket("127.0.0.1");
        Bucket ipv6Loopback = filter.resolveBucket("::1");

        assertSame(first, second);
        assertSame(first, ipv6Loopback);
    }

    @Test
    void resolveBucket_remoteIpShouldReusePerIpBucket() {
        Bucket first = filter.resolveBucket("10.1.1.10");
        Bucket second = filter.resolveBucket("10.1.1.10");
        Bucket third = filter.resolveBucket("10.1.1.11");

        assertSame(first, second);
        assertNotSame(first, third);
    }

    @Test
    void resolveBucket_shouldPartitionByProfile() {
        Bucket defaultBucket = filter.resolveBucket("10.1.1.10", RateLimitFilter.BucketProfile.DEFAULT);
        Bucket commentBucket = filter.resolveBucket("10.1.1.10", RateLimitFilter.BucketProfile.INTERACTION_COMMENT);

        assertNotSame(defaultBucket, commentBucket);
    }

    @Test
    void resolveProfile_shouldDetectSensitiveWriteEndpoints() {
        assertEquals(RateLimitFilter.BucketProfile.AUTH_REFRESH,
                filter.resolveProfile(request("POST", "/api/v1/auth/refresh")));
        assertEquals(RateLimitFilter.BucketProfile.INTERACTION_COMMENT,
                filter.resolveProfile(request("POST", "/api/v1/interactions/123/comments")));
        assertEquals(RateLimitFilter.BucketProfile.INTERACTION_LIKE,
                filter.resolveProfile(request("POST", "/api/v1/interactions/123/like")));
        assertEquals(RateLimitFilter.BucketProfile.SOCIAL_FOLLOW,
                filter.resolveProfile(request("POST", "/api/v1/users/abc/follow")));
        assertEquals(RateLimitFilter.BucketProfile.DEFAULT,
                filter.resolveProfile(request("GET", "/api/v1/leaderboards")));
    }

    @Test
    void resolveClientIp_shouldPreferForwardedForFirstHop() {
        MockHttpServletRequest request = request("POST", "/api/v1/interactions/123/comments");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.5");

        assertEquals("203.0.113.10", filter.resolveClientIp(request));
    }

    @Test
    void resolveBucketKey_shouldPreferBearerIdentityForSensitiveWrites() {
        MockHttpServletRequest request = request("POST", "/api/v1/interactions/123/comments");
        request.addHeader("Authorization", "Bearer sample-token");
        request.setRemoteAddr("198.51.100.3");

        String key = filter.resolveBucketKey(request, RateLimitFilter.BucketProfile.INTERACTION_COMMENT);

        assertEquals("bearer:" + Integer.toHexString("Bearer sample-token".hashCode()), key);
    }

    @Test
    void resolveBucketKey_shouldUseIpForDefaultProfile() {
        MockHttpServletRequest request = request("GET", "/api/v1/feed");
        request.setRemoteAddr("198.51.100.3");
        request.addHeader("Authorization", "Bearer sample-token");

        String key = filter.resolveBucketKey(request, RateLimitFilter.BucketProfile.DEFAULT);

        assertEquals("198.51.100.3", key);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
