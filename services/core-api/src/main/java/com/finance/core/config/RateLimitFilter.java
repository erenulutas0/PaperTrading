package com.finance.core.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter implements Filter {

    private static final Set<String> LOOPBACK_IPS = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    private final ConcurrentHashMap<String, Bucket> cache = new ConcurrentHashMap<>();
    private final Bucket localBypassBucket = Bucket.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(100000)
                    .refillGreedy(100000, Duration.ofMinutes(1))
                    .build())
            .build();

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(50)
                .refillGreedy(50, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public Bucket resolveBucket(String ip) {
        if (LOOPBACK_IPS.contains(ip)) {
            return localBypassBucket;
        }
        return cache.computeIfAbsent(ip, k -> createNewBucket());
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String ip = request.getRemoteAddr();
        Bucket bucket = resolveBucket(ip);

        // Allow 1 token to be consumed
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(servletRequest, servletResponse);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\": \"Too many requests - Rate limit exceeded. Try again later.\", \"status\": 429}");
        }
    }
}
