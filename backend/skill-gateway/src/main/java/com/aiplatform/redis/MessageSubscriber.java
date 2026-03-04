package com.aiplatform.redis;

import com.aiplatform.websocket.ConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Message Subscriber for Redis Pub/Sub
 *
 * Listens for messages on Redis channels and routes them to WebSocket connections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSubscriber implements MessageListener {

    private final ConnectionPool connectionPool;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            log.debug("Received message on channel: {}", channel);

            // Extract skill ID from channel
            String skillId = extractSkillId(channel);
            if (skillId != null) {
                // Route to WebSocket connection
                boolean sent = connectionPool.send(skillId, body);
                if (!sent) {
                    log.warn("Failed to route message to skill: {}", skillId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Redis message: {}", e.getMessage(), e);
        }
    }

    /**
     * Extract skill ID from channel name
     */
    private String extractSkillId(String channel) {
        // Channel format: skill:messages:{skillId}
        if (channel.startsWith("skill:messages:")) {
            String suffix = channel.substring("skill:messages:".length());
            // Handle instance channels: skill:messages:instance:{instanceId}
            if (suffix.startsWith("instance:")) {
                return null; // Not a skill-specific message
            }
            // Handle broadcast: skill:messages:broadcast
            if ("broadcast".equals(suffix)) {
                return null; // Broadcast handled separately
            }
            return suffix;
        }
        return null;
    }
}