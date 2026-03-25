package com.finance.core.config;

import com.finance.core.observability.WebSocketObservabilityService;
import com.finance.core.security.InvalidJwtException;
import com.finance.core.security.JwtTokenClaims;
import com.finance.core.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.UUID;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String LEGACY_NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    private final WebSocketRuntimeProperties webSocketRuntimeProperties;
    private final WebSocketObservabilityService webSocketObservabilityService;
    private final JwtTokenService jwtTokenService;

    @Autowired
    public StompAuthChannelInterceptor(
            WebSocketRuntimeProperties webSocketRuntimeProperties,
            @Autowired(required = false) WebSocketObservabilityService webSocketObservabilityService,
            @Autowired(required = false) JwtTokenService jwtTokenService) {
        this.webSocketRuntimeProperties = webSocketRuntimeProperties;
        this.webSocketObservabilityService = webSocketObservabilityService;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
            String userId = accessor.getFirstNativeHeader(USER_ID_HEADER);

            if (authorization != null && !authorization.isBlank()) {
                if (!authorization.startsWith("Bearer ")) {
                    throw reject(command, "invalid-authorization-header", "Invalid Authorization header");
                }
                if (jwtTokenService == null) {
                    throw reject(command, "jwt-service-unavailable", "JWT service unavailable");
                }

                String token = authorization.substring("Bearer ".length()).trim();
                JwtTokenClaims claims;
                try {
                    claims = jwtTokenService.parseAndValidate(token);
                } catch (InvalidJwtException e) {
                    throw reject(command, "invalid-jwt", "Invalid Bearer token");
                }

                String tokenUserId = claims.userId().toString();
                if (userId != null && !userId.isBlank() && !tokenUserId.equals(userId)) {
                    throw reject(command, "user-id-token-mismatch", "Authorization and X-User-Id mismatch");
                }

                accessor.setUser(new StompPrincipal(tokenUserId));
                return message;
            }

            if (userId == null || userId.isBlank()) {
                accessor.setUser(new StompPrincipal("anon-" + UUID.randomUUID()));
                return message;
            }

            validateUuid(userId, command, "invalid-user-id");
            accessor.setUser(new StompPrincipal(userId));
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(LEGACY_NOTIFICATION_TOPIC_PREFIX)) {
                if (!webSocketRuntimeProperties.isLegacyUserTopicBroadcastEnabled()) {
                    throw reject(command, "legacy-topic-disabled", "Legacy notification topic subscription is disabled");
                }
                Principal principal = accessor.getUser();
                if (principal == null) {
                    throw reject(command, "missing-principal", "Missing authenticated STOMP principal");
                }
                String requestedUserId = destination.substring(LEGACY_NOTIFICATION_TOPIC_PREFIX.length());
                validateUuid(requestedUserId, command, "invalid-requested-user-id");
                if (!principal.getName().equals(requestedUserId)) {
                    throw reject(command, "cross-user-subscribe", "Cannot subscribe to another user's notification topic");
                }
            }
        }

        return message;
    }

    private void validateUuid(String raw, StompCommand command, String reason) {
        try {
            UUID.fromString(raw);
        } catch (Exception e) {
            throw reject(command, reason, "Invalid UUID value: " + raw);
        }
    }

    private StompAuthException reject(StompCommand command, String code, String message) {
        recordStompError(command, code);
        return new StompAuthException(code, message);
    }

    private void recordStompError(StompCommand command, String reason) {
        if (webSocketObservabilityService == null) {
            return;
        }
        String normalizedCommand = command != null ? command.name() : "UNKNOWN";
        webSocketObservabilityService.recordStompError(normalizedCommand, reason);
    }
}
