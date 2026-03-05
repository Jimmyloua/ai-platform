package com.aiplatform.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Session Registry for managing session state in Redis.
 * Tracks agent and client endpoints for each session.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRegistry {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String SESSION_AGENT_KEY = "agent_endpoint";
    private static final String SESSION_CLIENT_KEY = "client_endpoint";
    private static final String SESSION_META_KEY = "metadata";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    /**
     * Register a session with client endpoint
     */
    public void registerClientEndpoint(String sessionId, SessionEndpoint endpoint) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            String endpointJson = objectMapper.writeValueAsString(endpoint);

            redisTemplate.opsForHash().put(key, SESSION_CLIENT_KEY, endpointJson);
            redisTemplate.expire(key, SESSION_TTL);

            log.info("Registered client endpoint for session: sessionId={}, gateway={}, client={}",
                    sessionId, endpoint.getGatewayId(), endpoint.getClientId());
        } catch (Exception e) {
            log.error("Failed to register client endpoint: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register client endpoint", e);
        }
    }

    /**
     * Register a session with agent endpoint
     */
    public void registerAgentEndpoint(String sessionId, SessionEndpoint endpoint) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            String endpointJson = objectMapper.writeValueAsString(endpoint);

            redisTemplate.opsForHash().put(key, SESSION_AGENT_KEY, endpointJson);
            redisTemplate.expire(key, SESSION_TTL);

            log.info("Registered agent endpoint for session: sessionId={}, gateway={}, plugin={}, agent={}",
                    sessionId, endpoint.getGatewayId(), endpoint.getPluginId(), endpoint.getAgentId());
        } catch (Exception e) {
            log.error("Failed to register agent endpoint: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to register agent endpoint", e);
        }
    }

    /**
     * Get client endpoint for a session
     */
    public Optional<SessionEndpoint> getClientEndpoint(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            Object value = redisTemplate.opsForHash().get(key, SESSION_CLIENT_KEY);

            if (value != null) {
                return Optional.of(objectMapper.readValue(value.toString(), SessionEndpoint.class));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get client endpoint: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Get agent endpoint for a session
     */
    public Optional<SessionEndpoint> getAgentEndpoint(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            Object value = redisTemplate.opsForHash().get(key, SESSION_AGENT_KEY);

            if (value != null) {
                return Optional.of(objectMapper.readValue(value.toString(), SessionEndpoint.class));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get agent endpoint: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Get the peer endpoint (the other side of the conversation)
     */
    public Optional<SessionEndpoint> getPeerEndpoint(String sessionId, SessionEndpoint.Type fromType) {
        if (fromType == SessionEndpoint.Type.AGENT) {
            return getClientEndpoint(sessionId);
        } else {
            return getAgentEndpoint(sessionId);
        }
    }

    /**
     * Update session metadata
     */
    public void updateMetadata(String sessionId, String key, String value) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            String metaKey = SESSION_META_KEY + ":" + key;
            redisTemplate.opsForHash().put(sessionKey, metaKey, value);
        } catch (Exception e) {
            log.error("Failed to update session metadata: {}", e.getMessage(), e);
        }
    }

    /**
     * Get session metadata
     */
    public Optional<String> getMetadata(String sessionId, String key) {
        try {
            String sessionKey = SESSION_KEY_PREFIX + sessionId;
            String metaKey = SESSION_META_KEY + ":" + key;
            Object value = redisTemplate.opsForHash().get(sessionKey, metaKey);
            return Optional.ofNullable(value != null ? value.toString() : null);
        } catch (Exception e) {
            log.error("Failed to get session metadata: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Check if session exists
     */
    public boolean sessionExists(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Refresh session TTL
     */
    public void refreshSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.expire(key, SESSION_TTL);
    }

    /**
     * Remove session (cleanup)
     */
    public void removeSession(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.delete(key);
            log.info("Removed session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to remove session: {}", e.getMessage(), e);
        }
    }

    /**
     * Remove endpoint from session
     */
    public void removeEndpoint(String sessionId, SessionEndpoint.Type type) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            String field = type == SessionEndpoint.Type.AGENT ? SESSION_AGENT_KEY : SESSION_CLIENT_KEY;
            redisTemplate.opsForHash().delete(key, field);
            log.info("Removed {} endpoint from session: {}", type, sessionId);
        } catch (Exception e) {
            log.error("Failed to remove endpoint: {}", e.getMessage(), e);
        }
    }

    /**
     * Set session TTL
     */
    public void setSessionTTL(String sessionId, Duration ttl) {
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.expire(key, ttl);
    }
}