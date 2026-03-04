package com.finance.core.security;

import java.util.UUID;

public record JwtTokenClaims(
        UUID userId,
        String username,
        long issuedAtEpochSeconds,
        long expiresAtEpochSeconds) {
}
