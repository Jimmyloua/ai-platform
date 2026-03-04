package com.aiplatform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway Connection Pool Manager
 *
 * Manages WebSocket connections for both agent and client endpoints.
 * Supports session-based routing between connections.
 */
@Slf4j
@Component
public class GatewayConnectionPool {

    // Active connections: connectionId -> message sink
    private final Map<String, Sinks.Many<String>> connections = new ConcurrentHashMap<>();

    // Connection metadata: connectionId -> metadata
    private final Map<String, ConnectionMetadata> metadata = new ConcurrentHashMap<>();

    // Session to connection mapping: sessionId -> connectionId
    private final Map<String, String> sessionConnections = new ConcurrentHashMap<>();

    // Reverse mapping: connectionId -> sessionId
    private final Map<String, String> connectionSessions = new ConcurrentHashMap<>();

    /**
     * Register a new connection
     */
    public void register(String connectionId, Sinks.Many<String> sink, ConnectionType type) {
        connections.put(connectionId, sink);
        metadata.put(connectionId, new ConnectionMetadata(
                connectionId,
                type,
                System.currentTimeMillis()
        ));
        log.info("Registered connection: id={}, type={}", connectionId, type);
    }

    /**
     * Unregister a connection
     */
    public void unregister(String connectionId) {
        Sinks.Many<String> sink = connections.remove(connectionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        metadata.remove(connectionId);

        // Clean up session mapping
        String sessionId = connectionSessions.remove(connectionId);
        if (sessionId != null) {
            sessionConnections.remove(sessionId);
            log.debug("Removed session mapping: sessionId={}, connectionId={}", sessionId, connectionId);
        }

        log.info("Unregistered connection: id={}", connectionId);
    }

    /**
     * Bind a connection to a session
     */
    public void bindToSession(String connectionId, String sessionId) {
        sessionConnections.put(sessionId, connectionId);
        connectionSessions.put(connectionId, sessionId);
        log.debug("Bound connection to session: connectionId={}, sessionId={}", connectionId, sessionId);
    }

    /**
     * Unbind a connection from a session
     */
    public void unbindFromSession(String connectionId) {
        String sessionId = connectionSessions.remove(connectionId);
        if (sessionId != null) {
            sessionConnections.remove(sessionId);
            log.debug("Unbound connection from session: connectionId={}, sessionId={}", connectionId, sessionId);
        }
    }

    /**
     * Send message to a specific connection
     */
    public boolean send(String connectionId, String message) {
        Sinks.Many<String> sink = connections.get(connectionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(message);
            if (result == Sinks.EmitResult.OK) {
                log.debug("Sent message to connection: id={}", connectionId);
                return true;
            } else {
                log.warn("Failed to send message to connection: id={}, result={}", connectionId, result);
                return false;
            }
        }
        log.warn("Connection not found: id={}", connectionId);
        return false;
    }

    /**
     * Send message to a session's connection
     */
    public boolean sendToSession(String sessionId, String message) {
        String connectionId = sessionConnections.get(sessionId);
        if (connectionId != null) {
            return send(connectionId, message);
        }
        log.warn("No connection found for session: sessionId={}", sessionId);
        return false;
    }

    /**
     * Check if connection exists
     */
    public boolean hasConnection(String connectionId) {
        return connections.containsKey(connectionId);
    }

    /**
     * Check if session has a connection
     */
    public boolean hasSessionConnection(String sessionId) {
        return sessionConnections.containsKey(sessionId);
    }

    /**
     * Get connection ID for a session
     */
    public String getConnectionForSession(String sessionId) {
        return sessionConnections.get(sessionId);
    }

    /**
     * Get session ID for a connection
     */
    public String getSessionForConnection(String connectionId) {
        return connectionSessions.get(connectionId);
    }

    /**
     * Get connection count
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Get session count
     */
    public int getSessionCount() {
        return sessionConnections.size();
    }

    /**
     * Get all active connection IDs
     */
    public Set<String> getActiveConnections() {
        return Set.copyOf(connections.keySet());
    }

    /**
     * Get connection metadata
     */
    public ConnectionMetadata getMetadata(String connectionId) {
        return metadata.get(connectionId);
    }

    /**
     * Get connection uptime
     */
    public Duration getUptime(String connectionId) {
        ConnectionMetadata meta = metadata.get(connectionId);
        if (meta != null) {
            return Duration.ofMillis(System.currentTimeMillis() - meta.connectedAt());
        }
        return Duration.ZERO;
    }

    /**
     * Connection type enum
     */
    public enum ConnectionType {
        AGENT,      // Plugin/agent connection
        CLIENT      // CUI/IM client connection
    }

    /**
     * Connection metadata record
     */
    public record ConnectionMetadata(
            String connectionId,
            ConnectionType type,
            long connectedAt
    ) {}
}