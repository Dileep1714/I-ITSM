package com.iitsm.backend.realtime;

import com.iitsm.backend.config.ItsmProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time updates over WebSocket with STOMP (a simple publish/subscribe protocol on top of WebSocket).
 *
 * <ul>
 *   <li>{@code /topic/incidents}: every incident change, for support staff's live dashboard and lists.</li>
 *   <li>{@code /user/queue/notifications}: a private queue per user, so the notification bell updates the moment a
 *       notification is stored, without polling.</li>
 * </ul>
 *
 * <p>The server only <em>pushes</em>: clients cannot send messages to the application. Authentication happens on
 * the STOMP CONNECT frame ({@link StompAuthInterceptor}).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String INCIDENTS_TOPIC = "/topic/incidents";
    public static final String NOTIFICATIONS_QUEUE = "/queue/notifications";

    private final ItsmProperties.Security security;
    private final StompAuthInterceptor authInterceptor;

    public WebSocketConfig(ItsmProperties.Security security, StompAuthInterceptor authInterceptor) {
        this.security = security;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(security.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
