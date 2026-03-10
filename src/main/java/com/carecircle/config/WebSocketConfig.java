package com.carecircle.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// =============================================================================
// 🧠 WEBSOCKET + STOMP ARCHITECTURE
//
// Raw WebSocket vs STOMP:
//   Raw WebSocket = a persistent bidirectional TCP-like connection.
//   Problem: raw bytes, no routing, no pub/sub — you'd build that yourself.
//
//   STOMP (Simple Text Oriented Messaging Protocol) = a thin messaging
//   protocol ON TOP of WebSocket. Adds:
//     - Topic subscriptions (/topic/...)
//     - Request/reply channels (/app/... → server handler)
//     - Connection lifecycle frames (CONNECT, SUBSCRIBE, SEND, DISCONNECT)
//
// Our flow:
//   1. Next.js connects to ws://localhost:8080/ws (with SockJS fallback)
//   2. Client subscribes to /topic/org/{orgId}/dashboard
//   3. When a caregiver marks a dose, MedicationService calls
//      SimpMessagingTemplate.convertAndSend("/topic/org/{orgId}/dashboard", payload)
//   4. All subscribed clients receive the update instantly — no polling
//
// SockJS Fallback:
//   Some corporate networks block WebSocket upgrades.
//   SockJS falls back to HTTP long-polling transparently.
//   The client sees the same API either way.
// =============================================================================

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 🧠 Simple in-memory broker for /topic destinations.
        // Messages sent to /topic/... are broadcast to all subscribers.
        // Production upgrade: replace with StompBrokerRelay pointing at
        // RabbitMQ's STOMP plugin for clustering + durability.
        registry.enableSimpleBroker("/topic");

        // /app prefix: client sends to /app/something → routed to @MessageMapping
        // We don't use @MessageMapping in this sprint (server pushes only),
        // but this prefix is required by the framework.
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // 🧠 allowedOrigins must match your Next.js URL.
                // Use allowedOriginPatterns("*") only in dev.
                .setAllowedOriginPatterns("http://localhost:3000", "http://localhost:*")
                // SockJS fallback for environments that block WebSocket upgrades
                .withSockJS();
    }
}