package com.finance.core.security;

import com.finance.core.domain.AppUser;
import com.finance.core.domain.RefreshToken;
import com.finance.core.observability.AuthSessionObservabilityService;
import com.finance.core.repository.RefreshTokenRepository;
import com.finance.core.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final JwtTokenService jwtTokenService;
    private final JwtRuntimeProperties jwtRuntimeProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final MeterRegistry meterRegistry;
    private final AuthSessionObservabilityService authSessionObservabilityService;

    @Autowired
    public AuthSessionService(
            JwtTokenService jwtTokenService,
            JwtRuntimeProperties jwtRuntimeProperties,
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) AuthSessionObservabilityService authSessionObservabilityService) {
        this.jwtTokenService = jwtTokenService;
        this.jwtRuntimeProperties = jwtRuntimeProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.meterRegistry = meterRegistry;
        this.authSessionObservabilityService = authSessionObservabilityService;
    }

    @Transactional
    public AuthSessionTokens issueSession(AppUser user) {
        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getUsername());
        AuthSessionTokens tokens = buildAndPersistSessionTokens(user.getId(), accessToken);
        recordSessionMetric("issue", "success");
        return tokens;
    }

    @Transactional
    public AuthSessionTokens refreshSession(String refreshTokenRaw) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hashRefreshToken(refreshTokenRaw))
                .orElseThrow(() -> invalidRefresh("Invalid refresh token"));

        if (existing.isRevoked() || existing.isExpired(now)) {
            throw invalidRefresh("Refresh token is expired or revoked");
        }

        AppUser user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> invalidRefresh("Refresh token user not found"));

        String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getUsername());
        AuthSessionTokens rotated = buildAndPersistSessionTokens(user.getId(), accessToken);

        existing.setRevoked(true);
        existing.setRevokedAt(now);
        existing.setLastUsedAt(now);
        existing.setReplacedByTokenHash(hashRefreshToken(rotated.refreshToken()));
        refreshTokenRepository.save(existing);

        if (authSessionObservabilityService != null) {
            authSessionObservabilityService.recordRefreshSuccess();
        }
        recordSessionMetric("refresh", "success");
        return rotated;
    }

    @Transactional
    public void revokeRefreshToken(String refreshTokenRaw) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashRefreshToken(refreshTokenRaw))
                .orElseThrow(() -> invalidRefresh("Invalid refresh token"));
        if (!token.isRevoked()) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            token.setRevoked(true);
            token.setRevokedAt(now);
            token.setLastUsedAt(now);
            refreshTokenRepository.save(token);
        }
        recordSessionMetric("revoke", "success");
    }

    @Transactional
    public int revokeAllSessions(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId, LocalDateTime.now(ZoneOffset.UTC));
        recordSessionMetric("revoke-all", "success");
        return revoked;
    }

    public void recordInvalidRefreshAttempt() {
        if (authSessionObservabilityService != null) {
            authSessionObservabilityService.recordInvalidRefreshAttempt();
        }
        recordSessionMetric("refresh", "invalid");
    }

    private AuthSessionTokens buildAndPersistSessionTokens(UUID userId, String accessToken) {
        String refreshTokenRaw = generateRefreshToken();
        String refreshTokenHash = hashRefreshToken(refreshTokenRaw);
        LocalDateTime refreshExpiresAt = LocalDateTime.now(ZoneOffset.UTC)
                .plus(jwtRuntimeProperties.normalizedRefreshTokenTtl());

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(refreshTokenHash)
                .expiresAt(refreshExpiresAt)
                .build();
        refreshTokenRepository.save(refreshToken);

        long accessTtlSec = jwtRuntimeProperties.normalizedAccessTokenTtl().toSeconds();
        long refreshTtlSec = jwtRuntimeProperties.normalizedRefreshTokenTtl().toSeconds();
        return new AuthSessionTokens(
                accessToken,
                refreshTokenRaw,
                accessTtlSec,
                refreshTtlSec);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private String hashRefreshToken(String refreshTokenRaw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshTokenRaw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash refresh token", e);
        }
    }

    private InvalidRefreshTokenException invalidRefresh(String message) {
        recordInvalidRefreshAttempt();
        return new InvalidRefreshTokenException(message);
    }

    private void recordSessionMetric(String operation, String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "app.auth.sessions.total",
                "operation", operation,
                "result", result)
                .increment();
    }
}
