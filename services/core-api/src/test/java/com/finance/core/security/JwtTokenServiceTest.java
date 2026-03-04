package com.finance.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void generateAndParse_shouldRoundTripClaims() {
        JwtRuntimeProperties properties = new JwtRuntimeProperties();
        properties.setSecret("unit-test-secret-value-with-sufficient-length-12345");
        properties.setIssuer("unit-test");
        JwtTokenService service = new JwtTokenService(properties, new ObjectMapper());

        UUID userId = UUID.randomUUID();
        String token = service.generateAccessToken(userId, "alice");

        JwtTokenClaims claims = service.parseAndValidate(token);
        assertEquals(userId, claims.userId());
        assertEquals("alice", claims.username());
        assertTrue(claims.expiresAtEpochSeconds() > claims.issuedAtEpochSeconds());
    }

    @Test
    void parseAndValidate_invalidToken_shouldThrow() {
        JwtRuntimeProperties properties = new JwtRuntimeProperties();
        properties.setSecret("unit-test-secret-value-with-sufficient-length-12345");
        JwtTokenService service = new JwtTokenService(properties, new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> service.parseAndValidate("not-a-token"));
    }
}
