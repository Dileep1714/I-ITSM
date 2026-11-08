package com.iitsm.backend.realtime;

import com.iitsm.backend.domain.user.Role;
import com.iitsm.backend.domain.user.User;
import com.iitsm.backend.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Security for the WebSocket channel, checked on every inbound STOMP frame.
 *
 * <ul>
 *   <li><b>CONNECT</b> must carry {@code Authorization: Bearer <token>}: the same JWT as the REST API, verified the
 *       same way, and the account must still be active. The connection's user is the token's subject (user id),
 *       which is what {@code /user/queue/...} destinations are addressed by.</li>
 *   <li><b>SUBSCRIBE</b> to {@code /topic/incidents} is for support staff only; anyone may subscribe to their own
 *       {@code /user/queue/...}. Everything else is refused.</li>
 *   <li><b>SEND</b> is refused: the channel is push-only.</li>
 * </ul>
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter;
    private final UserRepository users;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter converter, UserRepository users) {
        this.jwtDecoder = jwtDecoder;
        this.converter = converter;
        this.users = users;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscription(accessor.getUser(), accessor.getDestination());
            case SEND -> throw new AccessDeniedException("This channel is read-only");
            default -> { }
        }
        return message;
    }

    AbstractAuthenticationToken authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring("Bearer ".length()).strip());
        } catch (JwtException e) {
            throw new AccessDeniedException("Invalid or expired token");
        }
        User user = loadActiveUser(jwt.getSubject());
        if (user == null) {
            throw new AccessDeniedException("Account not found or deactivated");
        }
        return converter.convert(jwt);
    }

    void authorizeSubscription(Principal principal, String destination) {
        if (principal == null || destination == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        if (destination.startsWith("/user/queue/")) {
            return;
        }
        if (destination.equals(WebSocketConfig.INCIDENTS_TOPIC)) {
            User user = loadActiveUser(principal.getName());
            if (user != null && user.getRole() != Role.EMPLOYEE) {
                return;
            }
        }
        throw new AccessDeniedException("You cannot subscribe to " + destination);
    }

    private User loadActiveUser(String subject) {
        try {
            return users.findById(Long.parseLong(subject)).filter(User::isActive).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
