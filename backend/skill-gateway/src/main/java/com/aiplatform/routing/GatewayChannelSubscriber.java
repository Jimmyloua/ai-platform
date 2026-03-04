package com.aiplatform.routing;

import com.aiplatform.websocket.GatewayConnectionPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Subscriber for gateway-specific messages from Redis Pub/Sub.
 * Each gateway instance subscribes only to its own channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayChannelSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final GatewayConnectionPool connectionPool;

    @Value("${app.gateway.id:}")
    private String configuredGatewayId;

    private String gatewayId;

    @PostConstruct
    public void init() {
        this.gatewayId = getGatewayId();
        log.info("Gateway channel subscriber initialized for gateway: {}", gatewayId);
    }

    private String getGatewayId() {
        if (configuredGatewayId != null && !configuredGatewayId.isEmpty()) {
            return configuredGatewayId;
        }
        return System.getenv().getOrDefault("HOSTNAME",
                "gateway-" + java.util.UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Get the channel this gateway should subscribe to
     */
    public String getSubscriptionChannel() {
        return "gateway:" + gatewayId;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.debug("Received routed message on channel: {}", channel);

            // Parse the routed message
            RoutedMessage routedMessage = objectMapper.readValue(body, RoutedMessage.class);

            // Handle the routed message
            handleRoutedMessage(routedMessage);

        } catch (Exception e) {
            log.error("Error processing routed message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle a routed message received from another gateway
     */
    private void handleRoutedMessage(RoutedMessage message) {
        try {
            String targetConnectionId = message.getTargetConnectionId();

            if (targetConnectionId == null) {
                log.warn("Routed message has no target connection: sessionId={}",
                        message.getSessionId());
                return;
            }

            // Check if connection exists locally
            if (!connectionPool.hasConnection(targetConnectionId)) {
                log.warn("Target connection not found locally: connectionId={}, sessionId={}",
                        targetConnectionId, message.getSessionId());
                return;
            }

            // Forward the message to the local connection
            boolean sent = connectionPool.send(targetConnectionId, message.getPayload());

            if (sent) {
                log.debug("Forwarded routed message to local connection: connectionId={}, sessionId={}",
                        targetConnectionId, message.getSessionId());
            } else {
                log.warn("Failed to forward routed message: connectionId={}, sessionId={}",
                        targetConnectionId, message.getSessionId());
            }

        } catch (Exception e) {
            log.error("Error handling routed message: {}", e.getMessage(), e);
        }
    }
}