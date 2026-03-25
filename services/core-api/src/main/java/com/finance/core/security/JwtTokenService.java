package com.finance.core.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String PURPOSE_ACCESS = "access";
    private static final String PURPOSE_NOTIFICATION_STREAM = "notification-stream";

    private final JwtRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public String generateAccessToken(UUID userId, String username) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.normalizedAccessTokenTtl());

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId.toString());
        payload.put("preferred_username", username);
        payload.put("iss", properties.getIssuer());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", exp.getEpochSecond());

        String encodedHeader = base64UrlJson(header);
        String encodedPayload = base64UrlJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);
        return signingInput + "." + signature;
    }

    public String generateNotificationStreamToken(UUID userId) {
        Instant now = Instant.now();
        Instant exp = now.plus(properties.normalizedNotificationStreamTokenTtl());

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId.toString());
        payload.put("purpose", PURPOSE_NOTIFICATION_STREAM);
        payload.put("iss", properties.getIssuer());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", exp.getEpochSecond());

        String encodedHeader = base64UrlJson(header);
        String encodedPayload = base64UrlJson(payload);
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = sign(signingInput);
        return signingInput + "." + signature;
    }

    public UUID parseAndValidateNotificationStreamToken(String token) {
        JsonNode payloadNode = parseAndValidatePayload(token);
        String purpose = payloadNode.path("purpose").asText("");
        if (!PURPOSE_NOTIFICATION_STREAM.equals(purpose)) {
            throw new InvalidJwtException("Invalid JWT purpose");
        }
        return parseUserId(payloadNode);
    }

    public JwtTokenClaims parseAndValidate(String token) {
        try {
            JsonNode payloadNode = parseAndValidatePayload(token);
            String purpose = payloadNode.path("purpose").asText(PURPOSE_ACCESS);
            if (!PURPOSE_ACCESS.equals(purpose)) {
                throw new InvalidJwtException("Invalid JWT purpose");
            }

            UUID userId = parseUserId(payloadNode);
            long issuedAt = payloadNode.path("iat").asLong(0L);
            long expiresAt = payloadNode.path("exp").asLong(0L);
            String username = payloadNode.path("preferred_username").asText("");
            return new JwtTokenClaims(userId, username, issuedAt, expiresAt);
        } catch (InvalidJwtException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidJwtException("Invalid JWT token");
        }
    }

    private JsonNode parseAndValidatePayload(String token) {
        try {
            String[] parts = token != null ? token.split("\\.") : new String[0];
            if (parts.length != 3) {
                throw new InvalidJwtException("Invalid JWT format");
            }

            String encodedHeader = parts[0];
            String encodedPayload = parts[1];
            String encodedSignature = parts[2];

            String signingInput = encodedHeader + "." + encodedPayload;
            String expectedSignature = sign(signingInput);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    encodedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new InvalidJwtException("Invalid JWT signature");
            }

            JsonNode headerNode = objectMapper.readTree(URL_DECODER.decode(encodedHeader));
            if (!"HS256".equals(headerNode.path("alg").asText())) {
                throw new InvalidJwtException("Unsupported JWT algorithm");
            }

            JsonNode payloadNode = objectMapper.readTree(URL_DECODER.decode(encodedPayload));
            String issuer = payloadNode.path("iss").asText();
            if (issuer == null || issuer.isBlank() || !issuer.equals(properties.getIssuer())) {
                throw new InvalidJwtException("Invalid JWT issuer");
            }

            long issuedAt = payloadNode.path("iat").asLong(0L);
            long expiresAt = payloadNode.path("exp").asLong(0L);
            long now = Instant.now().getEpochSecond();
            if (expiresAt <= 0 || expiresAt <= now) {
                throw new InvalidJwtException("JWT expired");
            }
            if (issuedAt > 0 && issuedAt > now + 60) {
                throw new InvalidJwtException("JWT issued-at is invalid");
            }

            return payloadNode;
        } catch (InvalidJwtException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidJwtException("Invalid JWT token");
        }
    }

    private UUID parseUserId(JsonNode payloadNode) {
        String rawSub = payloadNode.path("sub").asText();
        try {
            return UUID.fromString(rawSub);
        } catch (Exception e) {
            throw new InvalidJwtException("Invalid JWT subject");
        }
    }

    private String base64UrlJson(Object value) {
        try {
            return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode JWT payload", e);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            byte[] signature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return URL_ENCODER.encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }
}
