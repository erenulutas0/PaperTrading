package com.finance.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.core.security.AuthSessionService;
import com.finance.core.security.JwtTokenClaims;
import com.finance.core.security.JwtTokenService;
import com.finance.core.web.ApiErrorResponse;
import com.finance.core.web.RequestCorrelation;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter implements Filter {

    static final String LEGACY_USER_ID_HEADER = "X-User-Id";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Set<String> LOOPBACK_IPS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    enum BucketProfile {
        DEFAULT,
        AUTH_REFRESH,
        INTERACTION_COMMENT,
        INTERACTION_LIKE,
        SOCIAL_FOLLOW,
        PORTFOLIO_WRITE,
        TRADE_WRITE,
        WATCHLIST_WRITE,
        ANALYSIS_WRITE,
        STRATEGY_BOT_WRITE
    }

    private final ConcurrentHashMap<String, Bucket> cache = new ConcurrentHashMap<>();
    private final JwtTokenService jwtTokenService;
    private final AuthSessionService authSessionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Bucket localBypassBucket = Bucket.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(100000)
                    .refillGreedy(100000, Duration.ofMinutes(1))
                    .build())
            .build();

    @Value("${app.rate-limit.default-capacity:50}")
    private int defaultCapacity = 50;

    @Value("${app.rate-limit.auth-refresh-capacity:20}")
    private int authRefreshCapacity = 20;

    @Value("${app.rate-limit.interaction-comment-capacity:12}")
    private int interactionCommentCapacity = 12;

    @Value("${app.rate-limit.interaction-like-capacity:60}")
    private int interactionLikeCapacity = 60;

    @Value("${app.rate-limit.social-follow-capacity:20}")
    private int socialFollowCapacity = 20;

    @Value("${app.rate-limit.portfolio-write-capacity:25}")
    private int portfolioWriteCapacity = 25;

    @Value("${app.rate-limit.trade-write-capacity:30}")
    private int tradeWriteCapacity = 30;

    @Value("${app.rate-limit.watchlist-write-capacity:40}")
    private int watchlistWriteCapacity = 40;

    @Value("${app.rate-limit.analysis-write-capacity:18}")
    private int analysisWriteCapacity = 18;

    @Value("${app.rate-limit.strategy-bot-write-capacity:10}")
    private int strategyBotWriteCapacity = 10;

    public RateLimitFilter(JwtTokenService jwtTokenService, AuthSessionService authSessionService) {
        this.jwtTokenService = jwtTokenService;
        this.authSessionService = authSessionService;
    }

    public Bucket resolveBucket(String ip) {
        return resolveBucket(ip, BucketProfile.DEFAULT);
    }

    Bucket resolveBucket(String key, BucketProfile profile) {
        if (LOOPBACK_IPS.contains(key)) {
            return localBypassBucket;
        }
        return cache.computeIfAbsent(profile.name() + ":" + key, ignored -> createNewBucket(profile));
    }

    BucketProfile resolveProfile(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if ("POST".equalsIgnoreCase(method) && "/api/v1/auth/refresh".equals(path)) {
            return BucketProfile.AUTH_REFRESH;
        }
        if ("POST".equalsIgnoreCase(method) && path.matches("^/api/v1/interactions/[^/]+/comments$")) {
            return BucketProfile.INTERACTION_COMMENT;
        }
        if ("POST".equalsIgnoreCase(method) && path.matches("^/api/v1/interactions/[^/]+/like$")) {
            return BucketProfile.INTERACTION_LIKE;
        }
        if (path.matches("^/api/v1/users/[^/]+/follow$") &&
                ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return BucketProfile.SOCIAL_FOLLOW;
        }
        if (path.matches("^/api/v1/portfolios(?:/[^/]+(?:/deposit|/visibility)?)?$") &&
                ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return BucketProfile.PORTFOLIO_WRITE;
        }
        if (path.matches("^/api/v1/trade/(buy|sell)$") && "POST".equalsIgnoreCase(method)) {
            return BucketProfile.TRADE_WRITE;
        }
        if (path.matches("^/api/v1/watchlists(?:$|/.*)") &&
                ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return BucketProfile.WATCHLIST_WRITE;
        }
        if (path.matches("^/api/v1/analysis-posts(?:$|/[^/]+)$") &&
                ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return BucketProfile.ANALYSIS_WRITE;
        }
        if (path.matches("^/api/v1/strategy-bots(?:$|/[^/]+(?:$|/runs(?:$|/[^/]+(?:/(?:execute|refresh|cancel|apply-reconciliation))?)?))$") &&
                ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method))) {
            return BucketProfile.STRATEGY_BOT_WRITE;
        }
        return BucketProfile.DEFAULT;
    }

    boolean shouldBypass(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/actuator/");
    }

    String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    String resolveBucketKey(HttpServletRequest request, BucketProfile profile) {
        String clientIp = resolveClientIp(request);
        if (LOOPBACK_IPS.contains(clientIp)) {
            return clientIp;
        }
        if (profile == BucketProfile.DEFAULT) {
            return clientIp;
        }

        String stablePrincipalKey = resolveStablePrincipalKey(request);
        if (stablePrincipalKey != null) {
            return stablePrincipalKey;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            return "bearer:" + Integer.toHexString(authorization.hashCode());
        }

        String legacyUserId = request.getHeader(LEGACY_USER_ID_HEADER);
        if (legacyUserId != null && !legacyUserId.isBlank()) {
            return "legacy:" + legacyUserId.trim();
        }

        return clientIp;
    }

    private String resolveStablePrincipalKey(HttpServletRequest request) {
        if (resolveProfile(request) == BucketProfile.AUTH_REFRESH) {
            String refreshToken = extractRefreshToken(request);
            if (refreshToken != null && !refreshToken.isBlank()) {
                return authSessionService.resolveRefreshTokenUserId(refreshToken)
                        .map(userId -> "refresh-user:" + userId)
                        .orElse(null);
            }
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring("Bearer ".length()).trim();
            try {
                JwtTokenClaims claims = jwtTokenService.parseAndValidate(token);
                return "principal:" + claims.userId();
            } catch (IllegalArgumentException ignored) {
                // Fall back to legacy/token-hash behavior for malformed or expired tokens.
            }
        }

        String legacyUserId = request.getHeader(LEGACY_USER_ID_HEADER);
        if (legacyUserId != null && !legacyUserId.isBlank()) {
            return "legacy:" + legacyUserId.trim();
        }

        return null;
    }

    private String extractRefreshToken(HttpServletRequest request) {
        try {
            if (!(request instanceof CachedBodyHttpServletRequest cachedRequest)) {
                return null;
            }
            byte[] body = cachedRequest.getCachedBody();
            if (body.length == 0) {
                return null;
            }
            return objectMapper.readTree(body).path("refreshToken").asText(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bucket createNewBucket(BucketProfile profile) {
        int capacity = switch (profile) {
            case AUTH_REFRESH -> authRefreshCapacity;
            case INTERACTION_COMMENT -> interactionCommentCapacity;
            case INTERACTION_LIKE -> interactionLikeCapacity;
            case SOCIAL_FOLLOW -> socialFollowCapacity;
            case PORTFOLIO_WRITE -> portfolioWriteCapacity;
            case TRADE_WRITE -> tradeWriteCapacity;
            case WATCHLIST_WRITE -> watchlistWriteCapacity;
            case ANALYSIS_WRITE -> analysisWriteCapacity;
            case STRATEGY_BOT_WRITE -> strategyBotWriteCapacity;
            case DEFAULT -> defaultCapacity;
        };

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (shouldBypass(request)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest requestToUse = shouldCacheBody(request)
                ? new CachedBodyHttpServletRequest(request)
                : request;

        BucketProfile profile = resolveProfile(requestToUse);
        String bucketKey = resolveBucketKey(requestToUse, profile);
        Bucket bucket = resolveBucket(bucketKey, profile);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(requestToUse, servletResponse);
        } else {
            writeRateLimitError(requestToUse, response);
        }
    }

    boolean shouldCacheBody(HttpServletRequest request) {
        return resolveProfile(request) == BucketProfile.AUTH_REFRESH;
    }

    void writeRateLimitError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestId = ensureRequestId(request, response);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(new ApiErrorResponse(
                "rate_limit_exceeded",
                "Too many requests - Rate limit exceeded. Try again later.",
                null,
                requestId)));
    }

    private String ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        Object existing = request.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE);
        String requestId = existing != null && !existing.toString().isBlank()
                ? existing.toString()
                : request.getHeader(RequestCorrelation.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        request.setAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestCorrelation.REQUEST_ID_HEADER, requestId);
        return requestId;
    }

    static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return inputStream.read();
                }

                @Override
                public boolean isFinished() {
                    return inputStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
