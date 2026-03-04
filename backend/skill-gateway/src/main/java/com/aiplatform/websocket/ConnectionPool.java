package com.aiplatform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Connection Pool Manager
 *
 * Manages WebSocket connections to skill servers.
 */
@Slf4j
@Component
public class ConnectionPool {

    // Active connections: skillId -> connection sink
    private final Map<String, Sinks.Many<String>> connections = new ConcurrentHashMap<>();

    // Connection metadata
    private final Map<String, ConnectionMetadata> metadata = new ConcurrentHashMap<>();

    /**
     * Register a new connection
     */
    public void register(String skillId, Sinks.Many<String> sink) {
        connections.put(skillId, sink);
        metadata.put(skillId, new ConnectionMetadata(skillId, System.currentTimeMillis()));
        log.info("Registered connection for skill: {}", skillId);
    }

    /**
     * Remove a connection
     */
    public void unregister(String skillId) {
        Sinks.Many<String> sink = connections.remove(skillId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        metadata.remove(skillId);
        log.info("Unregistered connection for skill: {}", skillId);
    }

    /**
     * Send message to a skill
     */
    public boolean send(String skillId, String message) {
        Sinks.Many<String> sink = connections.get(skillId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(message);
            return result == Sinks.EmitResult.OK;
        }
        log.warn("No connection found for skill: {}", skillId);
        return false;
    }

    /**
     * Broadcast message to all connections
     */
    public void broadcast(String message) {
        connections.forEach((skillId, sink) -> {
            sink.tryEmitNext(message);
        });
    }

    /**
     * Check if connection exists
     */
    public boolean hasConnection(String skillId) {
        return connections.containsKey(skillId);
    }

    /**
     * Get connection count
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Get all active skill IDs
     */
    public java.util.Set<String> getActiveSkills() {
        return connections.keySet();
    }

    /**
     * Get connection metadata
     */
    public ConnectionMetadata getMetadata(String skillId) {
        return metadata.get(skillId);
    }

    /**
     * Check connection health
     */
    public Mono<Boolean> isHealthy(String skillId) {
        return Mono.just(hasConnection(skillId));
    }

    /**
     * Get connection uptime
     */
    public Duration getUptime(String skillId) {
        ConnectionMetadata meta = metadata.get(skillId);
        if (meta != null) {
            return Duration.ofMillis(System.currentTimeMillis() - meta.connectedAt());
        }
        return Duration.ZERO;
    }

    /**
     * Connection metadata record
     */
    public record ConnectionMetadata(
            String skillId,
            long connectedAt
    ) {}
}