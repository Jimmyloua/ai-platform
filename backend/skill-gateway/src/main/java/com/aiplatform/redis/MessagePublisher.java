package com.aiplatform.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Message Publisher for Redis Pub/Sub
 *
 * Publishes messages to Redis channels for multi-instance communication.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePublisher {

    private final StringRedisTemplate redisTemplate;

    private static final String CHANNEL_PREFIX = "skill:messages:";

    /**
     * Publish message to a specific skill channel
     */
    public void publishToSkill(String skillId, String message) {
        String channel = CHANNEL_PREFIX + skillId;
        try {
            redisTemplate.convertAndSend(channel, message);
            log.debug("Published message to channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to publish message to channel {}: {}", channel, e.getMessage());
        }
    }

    /**
     * Publish message to broadcast channel
     */
    public void broadcast(String message) {
        String channel = CHANNEL_PREFIX + "broadcast";
        try {
            redisTemplate.convertAndSend(channel, message);
            log.debug("Broadcast message");
        } catch (Exception e) {
            log.error("Failed to broadcast message: {}", e.getMessage());
        }
    }

    /**
     * Publish to instance-specific channel
     */
    public void publishToInstance(String instanceId, String message) {
        String channel = CHANNEL_PREFIX + "instance:" + instanceId;
        try {
            redisTemplate.convertAndSend(channel, message);
            log.debug("Published message to instance channel: {}", channel);
        } catch (Exception e) {
            log.error("Failed to publish message to instance channel {}: {}", channel, e.getMessage());
        }
    }
}