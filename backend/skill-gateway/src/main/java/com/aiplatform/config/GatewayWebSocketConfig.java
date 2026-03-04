package com.aiplatform.config;

import com.aiplatform.websocket.GatewayWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket configuration for skill gateway.
 */
@Configuration
@RequiredArgsConstructor
public class GatewayWebSocketConfig {

    private final GatewayWebSocketHandler gatewayWebSocketHandler;

    @Value("${app.gateway.websocket.path:/ws/gateway}")
    private String websocketPath;

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, Object> map = new HashMap<>();

        // Main gateway endpoint - handles both agent and client connections
        map.put(websocketPath, gatewayWebSocketHandler);

        // Agent-specific endpoint
        map.put(websocketPath + "/agent", gatewayWebSocketHandler);

        // Client-specific endpoint
        map.put(websocketPath + "/client", gatewayWebSocketHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(1);
        mapping.setUrlMap(map);

        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}