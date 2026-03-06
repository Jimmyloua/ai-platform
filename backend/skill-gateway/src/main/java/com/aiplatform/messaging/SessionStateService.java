package com.aiplatform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for persisting session state to survive gateway restarts.
 * Stores session metadata, pending requests, and connection mappings in Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStateService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis key prefixes
    private static final String SESSION_KEY_PREFIX = "session:state:";
    private static final String PENDING_REQUEST_PREFIX = "session:pending:";
    private static final String CONNECTION_SESSION_PREFIX = "connection:sessions:";

    // Session TTL
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final Duration PENDING_TTL = Duration.ofMinutes(30);

    /**
     * Persist session state
     */
    public void persistSessionState(String sessionId, SessionState state) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            Map<String, String> stateMap = new HashMap<>();
            stateMap.put("sessionId", state.getSessionId());
            stateMap.put("clientGatewayId", state.getClientGatewayId() != null ? state.getClientGatewayId() : "");
            stateMap.put("clientConnectionId", state.getClientConnectionId() != null ? state.getClientConnectionId() : "");
            stateMap.put("clientId", state.getClientId() != null ? state.getClientId() : "");
            stateMap.put("agentGatewayId", state.getAgentGatewayId() != null ? state.getAgentGatewayId() : "");
            stateMap.put("agentConnectionId", state.getAgentConnectionId() != null ? state.getAgentConnectionId() : "");
            stateMap.put("pluginId", state.getPluginId() != null ? state.getPluginId() : "");
            stateMap.put("protocolType", state.getProtocolType() != null ? state.getProtocolType() : "");
            stateMap.put("status", state.getStatus() != null ? state.getStatus() : "active");
            stateMap.put("createdAt", String.valueOf(state.getCreatedAt()));
            stateMap.put("updatedAt", String.valueOf(System.currentTimeMillis()));

            redisTemplate.opsForHash().putAll(key, stateMap);
            redisTemplate.expire(key, SESSION_TTL);

            log.debug("Persisted session state: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to persist session state: {}", e.getMessage());
        }
    }

    /**
     * Get session state
     */
    public Optional<SessionState> getSessionState(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            Map<Object, Object> stateMap = redisTemplate.opsForHash().entries(key);

            if (stateMap.isEmpty()) {
                return Optional.empty();
            }

            SessionState state = SessionState.builder()
                    .sessionId((String) stateMap.get("sessionId"))
                    .clientGatewayId((String) stateMap.get("clientGatewayId"))
                    .clientConnectionId((String) stateMap.get("clientConnectionId"))
                    .clientId((String) stateMap.get("clientId"))
                    .agentGatewayId((String) stateMap.get("agentGatewayId"))
                    .agentConnectionId((String) stateMap.get("agentConnectionId"))
                    .pluginId((String) stateMap.get("pluginId"))
                    .protocolType((String) stateMap.get("protocolType"))
                    .status((String) stateMap.get("status"))
                    .createdAt(Long.parseLong((String) stateMap.getOrDefault("createdAt", "0")))
                    .build();

            return Optional.of(state);
        } catch (Exception e) {
            log.error("Failed to get session state: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Store pending request (for recovery)
     */
    public void storePendingRequest(String sessionId, String messageId, String requestJson) {
        try {
            String key = PENDING_REQUEST_PREFIX + sessionId;
            redisTemplate.opsForHash().put(key, messageId, requestJson);
            redisTemplate.expire(key, PENDING_TTL);

            log.debug("Stored pending request: sessionId={}, messageId={}", sessionId, messageId);
        } catch (Exception e) {
            log.error("Failed to store pending request: {}", e.getMessage());
        }
    }

    /**
     * Get pending requests for a session
     */
    public Map<String, String> getPendingRequests(String sessionId) {
        try {
            String key = PENDING_REQUEST_PREFIX + sessionId;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

            Map<String, String> result = new HashMap<>();
            entries.forEach((k, v) -> result.put(k.toString(), v.toString()));
            return result;
        } catch (Exception e) {
            log.error("Failed to get pending requests: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Remove pending request
     */
    public void removePendingRequest(String sessionId, String messageId) {
        try {
            String key = PENDING_REQUEST_PREFIX + sessionId;
            redisTemplate.opsForHash().delete(key, messageId);
            log.debug("Removed pending request: sessionId={}, messageId={}", sessionId, messageId);
        } catch (Exception e) {
            log.error("Failed to remove pending request: {}", e.getMessage());
        }
    }

    /**
     * Map connection to sessions (for cleanup on disconnect)
     */
    public void addConnectionSession(String connectionId, String sessionId) {
        try {
            String key = CONNECTION_SESSION_PREFIX + connectionId;
            redisTemplate.opsForSet().add(key, sessionId);
            redisTemplate.expire(key, SESSION_TTL);
        } catch (Exception e) {
            log.error("Failed to add connection session mapping: {}", e.getMessage());
        }
    }

    /**
     * Get sessions for a connection
     */
    public java.util.Set<String> getConnectionSessions(String connectionId) {
        try {
            String key = CONNECTION_SESSION_PREFIX + connectionId;
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Failed to get connection sessions: {}", e.getMessage());
            return java.util.Set.of();
        }
    }

    /**
     * Remove connection session mapping
     */
    public void removeConnectionSession(String connectionId, String sessionId) {
        try {
            String key = CONNECTION_SESSION_PREFIX + connectionId;
            redisTemplate.opsForSet().remove(key, sessionId);
        } catch (Exception e) {
            log.error("Failed to remove connection session mapping: {}", e.getMessage());
        }
    }

    /**
     * Update session status
     */
    public void updateSessionStatus(String sessionId, String status) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.opsForHash().put(key, "status", status);
            redisTemplate.opsForHash().put(key, "updatedAt", String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.error("Failed to update session status: {}", e.getMessage());
        }
    }

    /**
     * Delete session state
     */
    public void deleteSessionState(String sessionId) {
        try {
            String key = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.delete(key);

            String pendingKey = PENDING_REQUEST_PREFIX + sessionId;
            redisTemplate.delete(pendingKey);

            log.debug("Deleted session state: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Failed to delete session state: {}", e.getMessage());
        }
    }

    /**
     * Session state
     */
    @lombok.Data
    @lombok.Builder
    public static class SessionState {
        private String sessionId;
        private String clientGatewayId;
        private String clientConnectionId;
        private String clientId;
        private String agentGatewayId;
        private String agentConnectionId;
        private String pluginId;
        private String protocolType;
        private String status;
        private long createdAt;
    }
}