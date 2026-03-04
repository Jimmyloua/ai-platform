package com.aiplatform.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for routed messages via Redis Pub/Sub.
 * Publishes messages to specific gateway or plugin channels.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoutedMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String GATEWAY_CHANNEL_PREFIX = "gateway:";
    private static final String PLUGIN_CHANNEL_PREFIX = "plugin:";

    /**
     * Publish message to a specific gateway
     */
    public boolean publishToGateway(String gatewayId, RoutedMessage message) {
        try {
            String channel = GATEWAY_CHANNEL_PREFIX + gatewayId;
            String messageJson = objectMapper.writeValueAsString(message);

            redisTemplate.convertAndSend(channel, messageJson);

            log.debug("Published message to gateway channel: channel={}, sessionId={}",
                    channel, message.getSessionId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message to gateway: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Publish message to a specific plugin
     */
    public boolean publishToPlugin(String pluginId, RoutedMessage message) {
        try {
            String channel = PLUGIN_CHANNEL_PREFIX + pluginId;
            String messageJson = objectMapper.writeValueAsString(message);

            redisTemplate.convertAndSend(channel, messageJson);

            log.debug("Published message to plugin channel: channel={}, sessionId={}",
                    channel, message.getSessionId());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message to plugin: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Publish raw message to a gateway channel
     */
    public boolean publishRawToGateway(String gatewayId, String message) {
        try {
            String channel = GATEWAY_CHANNEL_PREFIX + gatewayId;
            redisTemplate.convertAndSend(channel, message);

            log.debug("Published raw message to gateway channel: {}", channel);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish raw message to gateway: {}", e.getMessage(), e);
            return false;
        }
    }
}