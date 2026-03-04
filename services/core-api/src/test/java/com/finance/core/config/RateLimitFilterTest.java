package com.finance.core.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

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
}
