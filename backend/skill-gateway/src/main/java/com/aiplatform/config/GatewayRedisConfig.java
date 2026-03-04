package com.aiplatform.config;

import com.aiplatform.routing.GatewayChannelSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

import java.util.UUID;

/**
 * Redis configuration for gateway channel subscription.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GatewayRedisConfig {

    private final GatewayChannelSubscriber gatewayChannelSubscriber;

    @Value("${app.gateway.id:}")
    private String configuredGatewayId;

    /**
     * Get the gateway ID
     */
    private String getGatewayId() {
        if (configuredGatewayId != null && !configuredGatewayId.isEmpty()) {
            return configuredGatewayId;
        }
        return System.getenv().getOrDefault("HOSTNAME",
                "gateway-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Redis message listener container for gateway channels
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Subscribe to this gateway's channel
        String gatewayChannel = "gateway:" + getGatewayId();
        container.addMessageListener(gatewayChannelSubscriber, new PatternTopic(gatewayChannel));

        log.info("Subscribed to gateway channel: {}", gatewayChannel);

        return container;
    }

    /**
     * String Redis template
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}