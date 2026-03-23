package com.finance.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.security.AuthSessionService;
import com.finance.core.security.JwtRuntimeProperties;
import com.finance.core.security.JwtTokenService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private final JwtRuntimeProperties jwtRuntimeProperties = new JwtRuntimeProperties();
    private final JwtTokenService jwtTokenService = new JwtTokenService(jwtRuntimeProperties, new ObjectMapper());
    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final RateLimitFilter filter = new RateLimitFilter(jwtTokenService, authSessionService);

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
        assertEquals(RateLimitFilter.BucketProfile.PORTFOLIO_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/portfolios")));
        assertEquals(RateLimitFilter.BucketProfile.PORTFOLIO_WRITE,
                filter.resolveProfile(request("PUT", "/api/v1/portfolios/123/visibility")));
        assertEquals(RateLimitFilter.BucketProfile.TRADE_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/trade/buy")));
        assertEquals(RateLimitFilter.BucketProfile.WATCHLIST_WRITE,
                filter.resolveProfile(request("PUT", "/api/v1/watchlists/items/123/alerts")));
        assertEquals(RateLimitFilter.BucketProfile.ANALYSIS_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/analysis-posts")));
        assertEquals(RateLimitFilter.BucketProfile.STRATEGY_BOT_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/strategy-bots")));
        assertEquals(RateLimitFilter.BucketProfile.STRATEGY_BOT_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/strategy-bots/123/runs")));
        assertEquals(RateLimitFilter.BucketProfile.STRATEGY_BOT_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/strategy-bots/123/runs/456/execute")));
        assertEquals(RateLimitFilter.BucketProfile.STRATEGY_BOT_WRITE,
                filter.resolveProfile(request("POST", "/api/v1/strategy-bots/123/runs/456/cancel")));
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
        String token = jwtTokenService.generateAccessToken(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "sample-user");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("198.51.100.3");

        String key = filter.resolveBucketKey(request, RateLimitFilter.BucketProfile.INTERACTION_COMMENT);

        assertEquals("principal:11111111-1111-1111-1111-111111111111", key);
    }

    @Test
    void resolveBucketKey_shouldPreferBearerIdentityForPortfolioWrites() {
        MockHttpServletRequest request = request("POST", "/api/v1/portfolios");
        String token = jwtTokenService.generateAccessToken(
                java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "portfolio-user");
        request.addHeader("Authorization", "Bearer " + token);
        request.setRemoteAddr("198.51.100.4");

        String key = filter.resolveBucketKey(request, RateLimitFilter.BucketProfile.PORTFOLIO_WRITE);

        assertEquals("principal:33333333-3333-3333-3333-333333333333", key);
    }

    @Test
    void resolveBucketKey_shouldFallBackToBearerHashWhenTokenIsInvalid() {
        MockHttpServletRequest request = request("POST", "/api/v1/interactions/123/comments");
        request.addHeader("Authorization", "Bearer invalid-token");
        request.setRemoteAddr("198.51.100.3");

        String key = filter.resolveBucketKey(request, RateLimitFilter.BucketProfile.INTERACTION_COMMENT);

        assertEquals("bearer:" + Integer.toHexString("Bearer invalid-token".hashCode()), key);
    }

    @Test
    void resolveBucketKey_shouldPreferRefreshTokenUserForAuthRefresh() {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/refresh");
        request.setContentType("application/json");
        request.setContent("{\"refreshToken\":\"refresh-123\"}".getBytes(StandardCharsets.UTF_8));
        request.setRemoteAddr("198.51.100.3");
        when(authSessionService.resolveRefreshTokenUserId("refresh-123"))
                .thenReturn(Optional.of(UUID.fromString("22222222-2222-2222-2222-222222222222")));

        String key = filter.resolveBucketKey(wrapForBody(request), RateLimitFilter.BucketProfile.AUTH_REFRESH);

        assertEquals("refresh-user:22222222-2222-2222-2222-222222222222", key);
    }

    @Test
    void resolveBucketKey_shouldFallBackToIpForUnknownRefreshToken() {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/refresh");
        request.setContentType("application/json");
        request.setContent("{\"refreshToken\":\"unknown-refresh\"}".getBytes(StandardCharsets.UTF_8));
        request.setRemoteAddr("198.51.100.3");
        when(authSessionService.resolveRefreshTokenUserId("unknown-refresh"))
                .thenReturn(Optional.empty());

        String key = filter.resolveBucketKey(wrapForBody(request), RateLimitFilter.BucketProfile.AUTH_REFRESH);

        assertEquals("198.51.100.3", key);
    }

    @Test
    void resolveBucketKey_shouldUseIpForDefaultProfile() {
        MockHttpServletRequest request = request("GET", "/api/v1/feed");
        request.setRemoteAddr("198.51.100.3");
        request.addHeader("Authorization", "Bearer sample-token");

        String key = filter.resolveBucketKey(request, RateLimitFilter.BucketProfile.DEFAULT);

        assertEquals("198.51.100.3", key);
    }

    @Test
    void shouldBypass_shouldSkipActuatorEndpoints() {
        assertEquals(true, filter.shouldBypass(request("GET", "/actuator/health")));
        assertEquals(false, filter.shouldBypass(request("GET", "/api/v1/leaderboards")));
    }

    @Test
    void writeRateLimitError_shouldReturnCorrelatedApiErrorPayload() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/interactions/123/comments");
        request.addHeader("X-Request-Id", "rate-limit-req-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.writeRateLimitError(request, response);

        assertEquals(429, response.getStatus());
        assertEquals("rate-limit-req-1", response.getHeader("X-Request-Id"));
        assertEquals(
                "{\"code\":\"rate_limit_exceeded\",\"message\":\"Too many requests - Rate limit exceeded. Try again later.\",\"details\":null,\"requestId\":\"rate-limit-req-1\"}",
                response.getContentAsString());
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private HttpServletRequest wrapForBody(MockHttpServletRequest request) {
        try {
            return new RateLimitFilter.CachedBodyHttpServletRequest(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
