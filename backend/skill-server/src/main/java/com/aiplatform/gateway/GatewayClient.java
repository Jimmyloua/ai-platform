package com.aiplatform.gateway;

import com.aiplatform.config.GatewayClientProperties;
import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket client for connecting from SkillServer to SkillGateway.
 * Routes client requests to Gateway for agent routing.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gateway.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayClient {

    private final GatewayClientProperties properties;
    private final ObjectMapper objectMapper;

    private final WebSocketClient webSocketClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession> sessionRef = new AtomicReference<>();

    // Response sinks for each session (sessionId -> sink)
    private final Map<String, Sinks.Many<WebSocketMessage.WebSocketMessageBase>> responseSinks = new ConcurrentHashMap<>();

    // Output sink for sending messages
    private final Sinks.Many<String> outputSink = Sinks.many().unicast().onBackpressureBuffer();

    // Gateway connection ID (assigned during registration)
    private volatile String connectionId;

    public GatewayClient(GatewayClientProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webSocketClient = new ReactorNettyWebSocketClient();
    }

    @PostConstruct
    public void init() {
        if (properties.isEnabled()) {
            log.info("Initializing gateway client: url={}", properties.getWsUrl());
            connect();
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
    }

    /**
     * Connect to the gateway
     */
    public synchronized void connect() {
        if (connected.get() || connecting.get()) {
            log.debug("Already connected or connecting");
            return;
        }

        connecting.set(true);
        log.info("Connecting to gateway: {}", properties.getWsUrl());

        try {
            URI uri = URI.create(properties.getWsUrl() + "/client");

            HttpHeaders headers = buildAuthHeaders();

            webSocketClient.execute(uri, headers, session -> {
                sessionRef.set(session);
                connected.set(true);
                connecting.set(false);
                log.info("Connected to gateway");

                // Handle incoming messages from gateway
                Flux<org.springframework.web.reactive.socket.WebSocketMessage> input = session.receive()
                        .doOnNext(msg -> handleIncomingMessage(msg.getPayloadAsText()))
                        .doOnError(e -> log.error("Error receiving from gateway: {}", e.getMessage()))
                        .onErrorResume(e -> Flux.empty());

                // Heartbeat
                Flux<String> heartbeat = Flux.interval(Duration.ofMillis(properties.getHeartbeatInterval()))
                        .map(i -> createHeartbeat());

                // Merge input handling with output
                return session.send(
                        Flux.merge(
                                input.map(m -> session.textMessage(m.getPayloadAsText())),
                                heartbeat.map(session::textMessage),
                                outputSink.asFlux().map(session::textMessage)
                        )
                ).then();
            }).subscribe(
                    v -> {},
                    error -> {
                        log.error("Gateway connection error: {}", error.getMessage());
                        handleDisconnection();
                    },
                    () -> {
                        log.info("Gateway connection completed");
                        handleDisconnection();
                    }
            );

        } catch (Exception e) {
            log.error("Failed to connect to gateway: {}", e.getMessage());
            connecting.set(false);
            scheduleReconnect();
        }
    }

    /**
     * Disconnect from gateway
     */
    public synchronized void disconnect() {
        if (!connected.get()) {
            return;
        }

        log.info("Disconnecting from gateway");
        org.springframework.web.reactive.socket.WebSocketSession session = sessionRef.get();
        if (session != null && session.isOpen()) {
            session.close().subscribe();
        }
        sessionRef.set(null);
        connected.set(false);
    }

    /**
     * Check if connected to gateway
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Get connection ID
     */
    public Optional<String> getConnectionId() {
        return Optional.ofNullable(connectionId);
    }

    /**
     * Route message to gateway and return response flux
     */
    public Flux<WebSocketMessage.WebSocketMessageBase> routeToGateway(
            String sessionId,
            WebSocketMessage.WebSocketMessageBase message) {

        return Flux.create(sink -> {
            if (!connected.get()) {
                log.warn("Not connected to gateway, cannot route message");
                sink.error(new RuntimeException("Gateway not connected"));
                return;
            }

            try {
                // Register response sink
                Sinks.Many<WebSocketMessage.WebSocketMessageBase> responseSink =
                        Sinks.many().unicast().onBackpressureBuffer();
                responseSinks.put(sessionId, responseSink);

                // Subscribe to responses
                responseSink.asFlux().subscribe(
                        sink::next,
                        sink::error,
                        sink::complete
                );

                // Send message
                String json = objectMapper.writeValueAsString(message);
                outputSink.tryEmitNext(json);

                log.debug("Routed message to gateway: sessionId={}, type={}", sessionId, message.getType());

            } catch (Exception e) {
                log.error("Error routing message to gateway: {}", e.getMessage());
                responseSinks.remove(sessionId);
                sink.error(e);
            }
        });
    }

    /**
     * Register a session for response handling
     */
    public void registerSession(String sessionId, Sinks.Many<WebSocketMessage.WebSocketMessageBase> responseSink) {
        responseSinks.put(sessionId, responseSink);
        log.debug("Registered session for responses: {}", sessionId);
    }

    /**
     * Unregister a session
     */
    public void unregisterSession(String sessionId) {
        Sinks.Many<WebSocketMessage.WebSocketMessageBase> sink = responseSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        log.debug("Unregistered session: {}", sessionId);
    }

    /**
     * Handle incoming message from gateway
     */
    private void handleIncomingMessage(String message) {
        try {
            log.debug("Received message from gateway: {}", message);

            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            String type = (String) parsed.get("type");
            String sessionId = (String) parsed.get("sessionId");

            // Handle session registration
            if ("session.registered".equals(type)) {
                this.connectionId = (String) parsed.get("connectionId");
                log.info("Gateway session registered: connectionId={}", connectionId);
                return;
            }

            // Route to session sink
            if (sessionId != null) {
                Sinks.Many<WebSocketMessage.WebSocketMessageBase> sink = responseSinks.get(sessionId);
                if (sink != null) {
                    WebSocketMessage.WebSocketMessageBase parsedMessage = parseMessage(message, type);
                    if (parsedMessage != null) {
                        sink.tryEmitNext(parsedMessage);

                        // Check if this is a terminal message
                        if (isTerminalMessage(type)) {
                            sink.tryEmitComplete();
                            responseSinks.remove(sessionId);
                        }
                    }
                } else {
                    log.warn("No response sink for session: {}", sessionId);
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming message: {}", e.getMessage());
        }
    }

    /**
     * Parse message based on type
     */
    private WebSocketMessage.WebSocketMessageBase parseMessage(String json, String type) {
        try {
            return switch (type) {
                case OpenCodeMessage.AgentResponseChunk.TYPE ->
                        objectMapper.readValue(json, OpenCodeMessage.AgentResponseChunk.class);
                case OpenCodeMessage.AgentResultResponse.TYPE ->
                        objectMapper.readValue(json, OpenCodeMessage.AgentResultResponse.class);
                case OpenCodeMessage.AgentErrorResponse.TYPE ->
                        objectMapper.readValue(json, OpenCodeMessage.AgentErrorResponse.class);
                case WebSocketMessage.SkillProgressResponse.TYPE ->
                        objectMapper.readValue(json, WebSocketMessage.SkillProgressResponse.class);
                case WebSocketMessage.SkillResultResponse.TYPE ->
                        objectMapper.readValue(json, WebSocketMessage.SkillResultResponse.class);
                case WebSocketMessage.SkillErrorResponse.TYPE ->
                        objectMapper.readValue(json, WebSocketMessage.SkillErrorResponse.class);
                default -> {
                    log.warn("Unknown message type: {}", type);
                    yield null;
                }
            };
        } catch (Exception e) {
            log.error("Error parsing message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if message type indicates end of stream
     */
    private boolean isTerminalMessage(String type) {
        return "agent.result".equals(type) || "agent.error".equals(type) ||
               "skill.result".equals(type) || "skill.error".equals(type);
    }

    /**
     * Handle disconnection
     */
    private void handleDisconnection() {
        connected.set(false);
        connecting.set(false);
        sessionRef.set(null);
        connectionId = null;

        // Complete all response sinks with error
        responseSinks.forEach((sessionId, sink) -> {
            try {
                sink.tryEmitError(new RuntimeException("Gateway disconnected"));
            } catch (Exception e) {
                log.debug("Error completing sink: {}", e.getMessage());
            }
        });
        responseSinks.clear();

        // Schedule reconnect
        if (properties.isEnabled()) {
            scheduleReconnect();
        }
    }

    /**
     * Schedule reconnection
     */
    private void scheduleReconnect() {
        log.info("Scheduling reconnect in {}ms", properties.getReconnectInterval());
        Mono.delay(Duration.ofMillis(properties.getReconnectInterval()))
                .subscribe(i -> connect());
    }

    /**
     * Build authentication headers
     */
    private HttpHeaders buildAuthHeaders() {
        long timestamp = System.currentTimeMillis();
        String signature = calculateSignature(timestamp);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Access-Key", properties.getAccessKey() != null ? properties.getAccessKey() : "");
        headers.set("X-Timestamp", String.valueOf(timestamp));
        headers.set("X-Signature", signature);
        return headers;
    }

    /**
     * Calculate signature for AK/SK authentication
     */
    private String calculateSignature(long timestamp) {
        // Simplified signature calculation
        // In production, use proper HMAC-SHA256
        String data = "GET\n/ws/gateway/client\n" + timestamp + "\n" +
                      (properties.getAccessKey() != null ? properties.getAccessKey() : "") + "\n";
        return UUID.randomUUID().toString(); // Placeholder
    }

    /**
     * Create heartbeat message
     */
    private String createHeartbeat() {
        try {
            return objectMapper.writeValueAsString(
                    WebSocketMessage.Heartbeat.builder()
                            .timestamp(System.currentTimeMillis())
                            .build()
            );
        } catch (Exception e) {
            return "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";
        }
    }
}