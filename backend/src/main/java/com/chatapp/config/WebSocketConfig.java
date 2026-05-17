package com.chatapp.config;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.chatapp.message.MessageController.StompUser;
import com.chatapp.security.JwtService;
import com.chatapp.user.User;
import com.chatapp.user.UserService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;
    private final UserService userService;
    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(
            JwtService jwtService,
            UserService userService,
            @Value("${app.cors.allowed-origins:}") String allowedOrigins
    ) {
        this.jwtService = jwtService;
        this.userService = userService;
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        this.allowedOrigins = origins.stream()
                .filter(origin -> !origin.contains("*"))
                .toArray(String[]::new);
        this.allowedOriginPatterns = origins.stream()
                .filter(origin -> origin.contains("*"))
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    @SuppressWarnings("null")
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/chat")
                .setAllowedOrigins(allowedOrigins)
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public @NonNull Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    List<String> authHeaders = accessor.getNativeHeader("Authorization");
                    String authHeader = authHeaders == null || authHeaders.isEmpty() ? null : authHeaders.get(0);
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("Missing websocket Authorization header");
                    }
                    String token = authHeader.substring(7);
                    String username = jwtService.extractUsername(token);
                    User user = (User) userService.loadUserByUsername(username);
                    if (!jwtService.isTokenValid(token, user)) {
                        throw new IllegalArgumentException("Invalid websocket token");
                    }
                    String userId = Objects.requireNonNull(user.getId());
                    String usernameForSession = Objects.requireNonNull(user.getUsername());
                    accessor.setUser(new StompUser(userId, usernameForSession));
                }
                return message;
            }
        });
    }
}
