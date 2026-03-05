package com.aiplatform.gateway;

import com.aiplatform.config.GatewayClientProperties;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WebSocket client for connecting to skill-gateway.
 * Handles AK/SK authentication, connection management, and message routing.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "gateway.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GatewayClient {

    private final GatewayClientProperties properties;
    private final ObjectMapper objectMapper;
    private final SignatureGenerator signatureGenerator;

    private final WebSocketClient webSocketClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicReference<org.springframework.web.reactive.socket.WebSocketSession> sessionRef = new AtomicReference<>();

    // Message sinks for each session
    private final Map<String, Sinks.Many<String>> sessionSinks = new ConcurrentHashMap<>();

    // Shared output sink for sending messages
    private final Sinks.Many<String> outputSink = Sinks.many().unicast().onBackpressureBuffer();

    public GatewayClient(GatewayClientProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.signatureGenerator = new SignatureGenerator();
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
            URI uri = URI.create(properties.getWsUrl());

            // Build auth headers for WebSocket handshake
            HttpHeaders headers = buildAuthHeaders();

            webSocketClient.execute(uri, headers, session -> {
                sessionRef.set(session);
                connected.set(true);
                connecting.set(false);
                log.info("Connected to gateway");

                // Handle incoming messages
                Flux<org.springframework.web.reactive.socket.WebSocketMessage> input = session.receive()
                        .doOnNext(msg -> handleIncomingMessage(msg.getPayloadAsText()))
                        .doOnError(e -> log.error("Error receiving from gateway: {}", e.getMessage()))
                        .onErrorResume(e -> Flux.empty());

                // Send outgoing messages
                Mono<Void> output = session.send(
                        outputSink.asFlux()
                                .map(session::textMessage)
                );

                // Heartbeat
                Flux<String> heartbeat = Flux.interval(Duration.ofMillis(properties.getHeartbeatInterval()))
                        .map(i -> createHeartbeat());

                // Merge input handling with heartbeat
                return session.send(
                        Flux.merge(
                                input.map(m -> session.textMessage(m.getPayloadAsText())),
                                heartbeat.map(session::textMessage)
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
     * Disconnect from the gateway
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
     * Send message to gateway
     */
    public void sendMessage(String sessionId, String message) {
        if (!connected.get()) {
            log.warn("Not connected to gateway, cannot send message");
            return;
        }

        log.debug("Sending message to gateway: sessionId={}", sessionId);
        outputSink.tryEmitNext(message);
    }

    /**
     * Register a session sink for receiving messages
     */
    public Sinks.Many<String> registerSession(String sessionId) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);
        log.debug("Registered session: {}", sessionId);
        return sink;
    }

    /**
     * Unregister a session sink
     */
    public void unregisterSession(String sessionId) {
        Sinks.Many<String> sink = sessionSinks.remove(sessionId);
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

            // Parse to get session ID
            Map<String, Object> parsed = objectMapper.readValue(message, Map.class);
            String sessionId = (String) parsed.get("sessionId");

            if (sessionId != null) {
                Sinks.Many<String> sink = sessionSinks.get(sessionId);
                if (sink != null) {
                    sink.tryEmitNext(message);
                } else {
                    log.warn("No sink for session: {}", sessionId);
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming message: {}", e.getMessage());
        }
    }

    /**
     * Handle disconnection
     */
    private void handleDisconnection() {
        connected.set(false);
        connecting.set(false);
        sessionRef.set(null);

        // Complete all session sinks
        sessionSinks.values().forEach(sink -> {
            try {
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.debug("Error completing sink: {}", e.getMessage());
            }
        });
        sessionSinks.clear();

        // Schedule reconnect
        if (properties.isEnabled()) {
            scheduleReconnect();
        }
    }

    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnect() {
        log.info("Scheduling reconnect in {}ms", properties.getReconnectInterval());
        Mono.delay(Duration.ofMillis(properties.getReconnectInterval()))
                .subscribe(i -> connect());
    }

    /**
     * Build authentication headers for WebSocket handshake
     */
    private HttpHeaders buildAuthHeaders() {
        long timestamp = System.currentTimeMillis();
        String signature = signatureGenerator.calculateSignature(
                "GET",
                "/ws/gateway",
                timestamp,
                properties.getAccessKey(),
                "", // No body for WebSocket handshake
                properties.getSecretKey()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Access-Key", properties.getAccessKey());
        headers.set("X-Timestamp", String.valueOf(timestamp));
        headers.set("X-Signature", signature);
        return headers;
    }

    /**
     * Create heartbeat message
     */
    private String createHeartbeat() {
        try {
            com.aiplatform.dto.WebSocketMessage.Heartbeat heartbeat =
                    com.aiplatform.dto.WebSocketMessage.Heartbeat.builder()
                            .timestamp(System.currentTimeMillis())
                            .build();
            return objectMapper.writeValueAsString(heartbeat);
        } catch (Exception e) {
            return "{\"type\":\"heartbeat\",\"timestamp\":" + System.currentTimeMillis() + "}";
        }
    }

    /**
     * Signature generator for AK/SK authentication
     */
    private static class SignatureGenerator {

        private static final String ALGORITHM = "HmacSHA256";
        private static final String HASH_ALGORITHM = "SHA-256";

        public String calculateSignature(String method, String path, long timestamp,
                                         String accessKey, String body, String secretKey) {
            try {
                // Calculate body hash
                String bodyHash = calculateBodyHash(body);

                // Build string to sign
                String stringToSign = buildStringToSign(method, path, timestamp, accessKey, bodyHash);

                // Sign with HMAC-SHA256
                return hmacSha256(stringToSign, secretKey);
            } catch (Exception e) {
                throw new RuntimeException("Failed to calculate signature", e);
            }
        }

        private String buildStringToSign(String method, String path, long timestamp,
                                         String accessKey, String bodyHash) {
            return new StringBuilder()
                    .append(method).append("\n")
                    .append(path).append("\n")
                    .append(timestamp).append("\n")
                    .append(accessKey).append("\n")
                    .append(bodyHash)
                    .toString();
        }

        private String calculateBodyHash(String body) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance(HASH_ALGORITHM);
                byte[] hash = digest.digest((body != null ? body : "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return java.util.Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                throw new RuntimeException("SHA-256 algorithm not available", e);
            }
        }

        private String hmacSha256(String data, String key) {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance(ALGORITHM);
                javax.crypto.spec.SecretKeySpec secretKeySpec =
                        new javax.crypto.spec.SecretKeySpec(key.getBytes(java.nio.charset.StandardCharsets.UTF_8), ALGORITHM);
                mac.init(secretKeySpec);
                byte[] hmac = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return java.util.Base64.getEncoder().encodeToString(hmac);
            } catch (Exception e) {
                throw new RuntimeException("Failed to compute HMAC", e);
            }
        }
    }
}