package com.finance.core.config;

import com.finance.core.security.JwtTokenClaims;
import com.finance.core.security.InvalidJwtException;
import com.finance.core.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class StompAuthChannelInterceptorTest {

    private WebSocketRuntimeProperties properties;
    private com.finance.core.observability.WebSocketObservabilityService observabilityService;
    private JwtTokenService jwtTokenService;
    private StompAuthChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        properties = new WebSocketRuntimeProperties();
        observabilityService = mock(com.finance.core.observability.WebSocketObservabilityService.class);
        jwtTokenService = mock(JwtTokenService.class);
        interceptor = new StompAuthChannelInterceptor(properties, observabilityService, jwtTokenService);
        channel = mock(MessageChannel.class);
    }

    @Test
    void preSend_connectWithValidUserId_shouldAttachPrincipal() {
        String userId = UUID.randomUUID().toString();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("X-User-Id", userId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> out = interceptor.preSend(message, channel);
        StompHeaderAccessor outAccessor = StompHeaderAccessor.wrap(out);
        Principal principal = outAccessor.getUser();

        assertNotNull(principal);
        assertEquals(userId, principal.getName());
    }

    @Test
    void preSend_connectWithoutUserId_shouldAttachAnonymousPrincipal() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> out = interceptor.preSend(message, channel);
        StompHeaderAccessor outAccessor = StompHeaderAccessor.wrap(out);
        assertNotNull(outAccessor.getUser());
        assertTrue(outAccessor.getUser().getName().startsWith("anon-"));
    }

    @Test
    void preSend_connectWithBearerToken_shouldAttachPrincipalFromToken() {
        String userId = UUID.randomUUID().toString();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer token-value");
        when(jwtTokenService.parseAndValidate("token-value"))
                .thenReturn(new JwtTokenClaims(UUID.fromString(userId), "alice", 100L, 200L));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> out = interceptor.preSend(message, channel);
        StompHeaderAccessor outAccessor = StompHeaderAccessor.wrap(out);
        assertNotNull(outAccessor.getUser());
        assertEquals(userId, outAccessor.getUser().getName());
    }

    @Test
    void preSend_connectWithBearerTokenAndMismatchedUserId_shouldReject() {
        String tokenUserId = UUID.randomUUID().toString();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer token-value");
        accessor.setNativeHeader("X-User-Id", UUID.randomUUID().toString());
        when(jwtTokenService.parseAndValidate("token-value"))
                .thenReturn(new JwtTokenClaims(UUID.fromString(tokenUserId), "alice", 100L, 200L));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        StompAuthException error = assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
        assertEquals("user-id-token-mismatch", error.code());
        verify(observabilityService).recordStompError(eq("CONNECT"), eq("user-id-token-mismatch"));
    }

    @Test
    void preSend_connectWithInvalidBearerToken_shouldReject() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Bearer invalid-token");
        when(jwtTokenService.parseAndValidate("invalid-token"))
                .thenThrow(new InvalidJwtException("Invalid token"));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        StompAuthException error = assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
        assertEquals("invalid-jwt", error.code());
        verify(observabilityService).recordStompError(eq("CONNECT"), eq("invalid-jwt"));
    }

    @Test
    void preSend_subscribeLegacyTopicDisabled_shouldReject() {
        String userId = UUID.randomUUID().toString();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/notifications/" + userId);
        accessor.setUser(new StompPrincipal(userId));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        StompAuthException error = assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
        assertEquals("legacy-topic-disabled", error.code());
        verify(observabilityService).recordStompError(eq("SUBSCRIBE"), eq("legacy-topic-disabled"));
    }

    @Test
    void preSend_subscribeLegacyTopicEnabledButOtherUser_shouldReject() {
        properties.setLegacyUserTopicBroadcastEnabled(true);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination("/topic/notifications/" + UUID.randomUUID());
        accessor.setUser(new StompPrincipal(UUID.randomUUID().toString()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        StompAuthException error = assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));
        assertEquals("cross-user-subscribe", error.code());
        verify(observabilityService).recordStompError(eq("SUBSCRIBE"), eq("cross-user-subscribe"));
    }

    @Test
    void preSend_connectWithInvalidAuthorizationHeader_shouldRejectWithExplicitCode() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("Authorization", "Token invalid-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        StompAuthException error = assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));

        assertEquals("invalid-authorization-header", error.code());
        verify(observabilityService).recordStompError(eq("CONNECT"), eq("invalid-authorization-header"));
    }

    @Test
    void preSend_connectWithInvalidUserId_shouldRejectWithExplicitCode() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader("X-User-Id", "not-a-uuid");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        StompAuthException error = assertThrows(StompAuthException.class, () -> interceptor.preSend(message, channel));

        assertEquals("invalid-user-id", error.code());
        verify(observabilityService).recordStompError(eq("CONNECT"), eq("invalid-user-id"));
    }
}
