package com.finance.core.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration for real-time notifications and live data.
 * 
 * Client connects to: ws://localhost:8080/ws
 * Subscribes to: /user/{userId}/queue/notifications (personal)
 * /topic/market (broadcast)
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final WebSocketRuntimeProperties webSocketRuntimeProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        if (webSocketRuntimeProperties.isRelayBrokerMode()) {
            // External broker relay mode for multi-instance fanout consistency.
            StompBrokerRelayRegistration relay = config.enableStompBrokerRelay("/topic", "/queue")
                    .setRelayHost(webSocketRuntimeProperties.getRelayHost())
                    .setRelayPort(webSocketRuntimeProperties.getRelayPort())
                    .setClientLogin(webSocketRuntimeProperties.getRelayClientLogin())
                    .setClientPasscode(webSocketRuntimeProperties.getRelayClientPasscode())
                    .setSystemLogin(webSocketRuntimeProperties.getRelaySystemLogin())
                    .setSystemPasscode(webSocketRuntimeProperties.getRelaySystemPasscode())
                    .setSystemHeartbeatSendInterval(webSocketRuntimeProperties.getServerHeartbeatMs())
                    .setSystemHeartbeatReceiveInterval(webSocketRuntimeProperties.getClientHeartbeatMs());

            String virtualHost = webSocketRuntimeProperties.getRelayVirtualHost();
            if (virtualHost != null && !virtualHost.isBlank()) {
                relay.setVirtualHost(virtualHost);
            }
        } else {
            // Simple in-memory broker mode for local/dev.
            ThreadPoolTaskScheduler scheduler = webSocketBrokerTaskScheduler();
            SimpleBrokerRegistration simpleBroker = config.enableSimpleBroker("/topic", "/queue")
                    .setHeartbeatValue(new long[] {
                            webSocketRuntimeProperties.getServerHeartbeatMs(),
                            webSocketRuntimeProperties.getClientHeartbeatMs()
                    });
            simpleBroker.setTaskScheduler(scheduler);
        }

        // Prefix for messages FROM client -> server (e.g. @MessageMapping)
        config.setApplicationDestinationPrefixes("/app");
        // User-specific destination prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setSendTimeLimit(webSocketRuntimeProperties.getSendTimeLimitMs());
        registry.setSendBufferSizeLimit(webSocketRuntimeProperties.getSendBufferSizeLimitBytes());
        registry.setMessageSizeLimit(webSocketRuntimeProperties.getMessageSizeLimitBytes());
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = webSocketRuntimeProperties.getAllowedOriginPatterns()
                .toArray(String[]::new);

        // STOMP endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();

        // Plain WebSocket endpoint (no SockJS)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Bean
    public ThreadPoolTaskScheduler webSocketBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-broker-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
