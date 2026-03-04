package com.aiplatform.websocket;

import com.aiplatform.routing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main WebSocket handler for skill gateway.
 * Handles both agent (plugin) and client (CUI/IM) connections.
 * Routes messages between endpoints using session-affinity routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final GatewayConnectionPool connectionPool;
    private final SessionRegistry sessionRegistry;
    private final GatewayRouter gatewayRouter;

    @Value("${app.gateway.heartbeat-interval:30000}")
    private long heartbeatInterval;

    // Active output sinks for each session
    private final Map<String, Sinks.Many<String>> outputSinks = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String connectionId = UUID.randomUUID().toString();
        log.info("WebSocket connection established: connectionId={}", connectionId);

        // Create output sink
        Sinks.Many<String> outputSink = Sinks.many().unicast().onBackpressureBuffer();
        outputSinks.put(connectionId, outputSink);

        // Determine connection type from handshake
        GatewayConnectionPool.ConnectionType connectionType = determineConnectionType(session);

        // Register connection
        connectionPool.register(connectionId, outputSink, connectionType);

        // Handle incoming messages
        Flux<String> inputMessages = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> handleMessage(connectionId, connectionType, msg))
                .doOnError(e -> log.error("Error receiving message: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());

        // Heartbeat
        Flux<String> heartbeat = Flux.interval(Duration.ofMillis(heartbeatInterval))
                .map(i -> createHeartbeat())
                .doOnSubscribe(s -> log.debug("Started heartbeat for connection: {}", connectionId));

        // Outgoing messages
        Flux<String> outputMessages = Flux.merge(
                outputSink.asFlux(),
                heartbeat
        );

        return session.send(
                outputMessages
                        .map(session::textMessage)
                        .doOnNext(msg -> log.debug("Sending message to connection: {}", connectionId))
        )
                .doFinally(signal -> {
                    log.info("WebSocket connection closed: connectionId={}, signal={}", connectionId, signal);
                    cleanup(connectionId);
                });
    }

    /**
     * Determine connection type from session handshake
     */
    private GatewayConnectionPool.ConnectionType determineConnectionType(WebSocketSession session) {
        // Check for connection type in handshake headers or URI
        String path = session.getHandshakeInfo().getUri().getPath();
        if (path.contains("/agent") || path.contains("/plugin")) {
            return GatewayConnectionPool.ConnectionType.AGENT;
        }
        return GatewayConnectionPool.ConnectionType.CLIENT;
    }

    /**
     * Handle incoming WebSocket message
     */
    private void handleMessage(String connectionId, GatewayConnectionPool.ConnectionType connectionType,
                               String message) {
        try {
            log.debug("Received message: connectionId={}, type={}", connectionId, connectionType);

            // Parse message to get type and session ID
            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            String type = (String) parsed.get("type");
            String sessionId = (String) parsed.get("sessionId");

            if (type == null) {
                log.warn("Message missing type field: connectionId={}", connectionId);
                return;
            }

            // Handle different message types
            switch (type) {
                case "session.register" -> handleSessionRegister(connectionId, connectionType, message, parsed);
                case "session.unregister" -> handleSessionUnregister(connectionId, message, parsed);
                case "skill.execute", "agent.execute" -> handleExecuteRequest(connectionId, connectionType, message, parsed);
                case "skill.progress", "skill.result", "skill.error" -> handleSkillResponse(connectionId, message, parsed);
                case "agent.response.chunk", "agent.result", "agent.error" -> handleAgentResponse(connectionId, message, parsed);
                case "heartbeat" -> handleHeartbeat(connectionId);
                default -> log.debug("Unhandled message type: {}", type);
            }

        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle session registration
     */
    @SuppressWarnings("unchecked")
    private void handleSessionRegister(String connectionId, GatewayConnectionPool.ConnectionType connectionType,
                                        String message, Map<String, Object> parsed) {
        try {
            String sessionId = (String) parsed.get("sessionId");
            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
            }

            // Bind connection to session
            connectionPool.bindToSession(connectionId, sessionId);

            // Register endpoint in Redis
            Map<String, Object> payload = (Map<String, Object>) parsed.get("payload");
            String protocolType = payload != null ? (String) payload.get("protocolType") : "skill";

            if (connectionType == GatewayConnectionPool.ConnectionType.AGENT) {
                String pluginId = payload != null ? (String) payload.get("pluginId") : null;
                String agentId = payload != null ? (String) payload.get("agentId") : null;

                SessionEndpoint endpoint = SessionEndpoint.forAgent(
                        gatewayRouter.getGatewayId(),
                        connectionId,
                        pluginId,
                        agentId,
                        protocolType
                );
                sessionRegistry.registerAgentEndpoint(sessionId, endpoint);
            } else {
                String clientId = payload != null ? (String) payload.get("clientId") : null;

                SessionEndpoint endpoint = SessionEndpoint.forClient(
                        gatewayRouter.getGatewayId(),
                        connectionId,
                        clientId,
                        protocolType
                );
                sessionRegistry.registerClientEndpoint(sessionId, endpoint);
            }

            // Send acknowledgment
            sendAck(connectionId, sessionId);

            log.info("Session registered: sessionId={}, connectionId={}, type={}",
                    sessionId, connectionId, connectionType);

        } catch (Exception e) {
            log.error("Error registering session: {}", e.getMessage(), e);
            sendError(connectionId, null, "REGISTER_ERROR", e.getMessage());
        }
    }

    /**
     * Handle session unregistration
     */
    private void handleSessionUnregister(String connectionId, String message, Map<String, Object> parsed) {
        try {
            String sessionId = (String) parsed.get("sessionId");
            if (sessionId == null) {
                sessionId = connectionPool.getSessionForConnection(connectionId);
            }

            if (sessionId != null) {
                GatewayConnectionPool.ConnectionType type = connectionPool.getMetadata(connectionId).type();
                sessionRegistry.removeEndpoint(sessionId,
                        type == GatewayConnectionPool.ConnectionType.AGENT
                                ? SessionEndpoint.Type.AGENT
                                : SessionEndpoint.Type.CLIENT);
                connectionPool.unbindFromSession(connectionId);
            }

            log.info("Session unregistered: sessionId={}, connectionId={}", sessionId, connectionId);

        } catch (Exception e) {
            log.error("Error unregistering session: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle execute request (from client to agent)
     */
    private void handleExecuteRequest(String connectionId, GatewayConnectionPool.ConnectionType connectionType,
                                       String message, Map<String, Object> parsed) {
        try {
            String sessionId = (String) parsed.get("sessionId");
            String type = (String) parsed.get("type");

            if (sessionId == null) {
                sessionId = UUID.randomUUID().toString();
                parsed.put("sessionId", sessionId);
            }

            // If this is a client, register the session if not already registered
            if (connectionType == GatewayConnectionPool.ConnectionType.CLIENT) {
                if (!connectionPool.hasSessionConnection(sessionId)) {
                    connectionPool.bindToSession(connectionId, sessionId);

                    SessionEndpoint endpoint = SessionEndpoint.forClient(
                            gatewayRouter.getGatewayId(),
                            connectionId,
                            null,
                            type.startsWith("agent") ? "opencode" : "skill"
                    );
                    sessionRegistry.registerClientEndpoint(sessionId, endpoint);
                }
            }

            // Route message to agent
            boolean routed = gatewayRouter.routeToAgent(
                    sessionId,
                    connectionId,
                    type.startsWith("agent") ? "opencode" : "skill",
                    type,
                    message
            );

            if (!routed) {
                log.warn("Failed to route execute request: sessionId={}", sessionId);
                // Send to skill-server via Redis if no agent registered
                // This allows the skill-server to pick up the session
            }

            log.debug("Execute request routed: sessionId={}, type={}", sessionId, type);

        } catch (Exception e) {
            log.error("Error handling execute request: {}", e.getMessage(), e);
            sendError(connectionId, null, "ROUTE_ERROR", e.getMessage());
        }
    }

    /**
     * Handle skill response (from agent to client)
     */
    private void handleSkillResponse(String connectionId, String message, Map<String, Object> parsed) {
        handleResponseToClient(connectionId, message, parsed);
    }

    /**
     * Handle agent response (from agent to client)
     */
    private void handleAgentResponse(String connectionId, String message, Map<String, Object> parsed) {
        handleResponseToClient(connectionId, message, parsed);
    }

    /**
     * Handle response to client
     */
    private void handleResponseToClient(String connectionId, String message, Map<String, Object> parsed) {
        try {
            String sessionId = (String) parsed.get("sessionId");
            String type = (String) parsed.get("type");

            if (sessionId == null) {
                log.warn("Response missing sessionId: connectionId={}", connectionId);
                return;
            }

            // Get plugin ID if available
            String pluginId = null;
            GatewayConnectionPool.ConnectionMetadata meta = connectionPool.getMetadata(connectionId);
            if (meta != null && meta.type() == GatewayConnectionPool.ConnectionType.AGENT) {
                // Could get plugin ID from metadata or parsed message
            }

            // Route message to client
            boolean routed = gatewayRouter.routeToClient(
                    sessionId,
                    connectionId,
                    pluginId,
                    type.startsWith("agent") ? "opencode" : "skill",
                    type,
                    message
            );

            if (!routed) {
                log.warn("Failed to route response to client: sessionId={}", sessionId);
            }

            log.debug("Response routed to client: sessionId={}, type={}", sessionId, type);

        } catch (Exception e) {
            log.error("Error handling response: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle heartbeat
     */
    private void handleHeartbeat(String connectionId) {
        Sinks.Many<String> sink = outputSinks.get(connectionId);
        if (sink != null) {
            String response = createHeartbeat();
            sink.tryEmitNext(response);
        }
    }

    /**
     * Send acknowledgment
     */
    private void sendAck(String connectionId, String sessionId) {
        Sinks.Many<String> sink = outputSinks.get(connectionId);
        if (sink != null) {
            try {
                String ack = objectMapper.writeValueAsString(Map.of(
                        "type", "session.registered",
                        "sessionId", sessionId,
                        "connectionId", connectionId,
                        "timestamp", System.currentTimeMillis()
                ));
                sink.tryEmitNext(ack);
            } catch (Exception e) {
                log.error("Error sending ack: {}", e.getMessage());
            }
        }
    }

    /**
     * Send error
     */
    private void sendError(String connectionId, String sessionId, String code, String message) {
        Sinks.Many<String> sink = outputSinks.get(connectionId);
        if (sink != null) {
            try {
                String error = objectMapper.writeValueAsString(Map.of(
                        "type", "error",
                        "sessionId", sessionId,
                        "code", code,
                        "message", message,
                        "timestamp", System.currentTimeMillis()
                ));
                sink.tryEmitNext(error);
            } catch (Exception e) {
                log.error("Error sending error: {}", e.getMessage());
            }
        }
    }

    /**
     * Create heartbeat message
     */
    private String createHeartbeat() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "heartbeat",
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";
        }
    }

    /**
     * Cleanup connection resources
     */
    private void cleanup(String connectionId) {
        Sinks.Many<String> sink = outputSinks.remove(connectionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        connectionPool.unregister(connectionId);
    }

    /**
     * Send message to a specific connection (called by GatewayChannelSubscriber)
     */
    public void sendToConnection(String connectionId, String message) {
        Sinks.Many<String> sink = outputSinks.get(connectionId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
    }
}