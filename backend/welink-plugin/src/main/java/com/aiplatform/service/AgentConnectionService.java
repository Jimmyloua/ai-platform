package com.aiplatform.service;

import com.aiplatform.agent.ExternalAgentClient;
import com.aiplatform.config.GatewayClientProperties;
import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.gateway.GatewayClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for WeLinkPlugin to act as an AGENT connected to SkillGateway.
 * Receives routed messages from Gateway and forwards to ExternalAgentClient.
 *
 * Call flow: CUI -> SkillServer -> SkillGateway -> WeLinkPlugin (this) -> ExternalAgent
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "gateway.client", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentConnectionService {

    private final GatewayClient gatewayClient;
    private final GatewayClientProperties gatewayProperties;
    private final ExternalAgentClient externalAgentClient;
    private final ObjectMapper objectMapper;

    // Active sessions: sessionId -> response sink
    private final Map<String, Sinks.Many<WebSocketMessage.WebSocketMessageBase>> activeSessions = new ConcurrentHashMap<>();

    // Plugin ID for agent registration
    private static final String PLUGIN_ID = "welink-plugin";
    private static final String AGENT_ID = "opencode-agent";

    @PostConstruct
    public void init() {
        log.info("Initializing AgentConnectionService as agent: pluginId={}", PLUGIN_ID);

        // Wait for gateway connection then register as agent
        if (gatewayClient.isConnected()) {
            registerAsAgent();
        } else {
            // Subscribe to connection state changes
            scheduleRegistration();
        }
    }

    /**
     * Schedule agent registration after connection
     */
    private void scheduleRegistration() {
        Flux.interval(Duration.ofSeconds(2))
                .filter(i -> gatewayClient.isConnected())
                .take(1)
                .subscribe(i -> registerAsAgent());
    }

    /**
     * Register this plugin as an agent with the gateway
     */
    private void registerAsAgent() {
        try {
            String sessionId = UUID.randomUUID().toString();

            // Create registration message
            Map<String, Object> registration = Map.of(
                    "type", "session.register",
                    "sessionId", sessionId,
                    "payload", Map.of(
                            "pluginId", PLUGIN_ID,
                            "agentId", AGENT_ID,
                            "protocolType", "opencode",
                            "capabilities", java.util.List.of("streaming", "tools")
                    )
            );

            String json = objectMapper.writeValueAsString(registration);

            // Create response sink
            Sinks.Many<String> responseSink = Sinks.many().unicast().onBackpressureBuffer();

            // Register with gateway
            gatewayClient.registerSession(sessionId, convertSink(responseSink));

            // Handle responses
            responseSink.asFlux()
                    .subscribe(
                            response -> handleGatewayResponse(response),
                            error -> log.error("Agent registration error: {}", error.getMessage()),
                            () -> log.info("Agent registration completed")
                    );

            log.info("Registered as agent: pluginId={}, agentId={}", PLUGIN_ID, AGENT_ID);

        } catch (Exception e) {
            log.error("Failed to register as agent: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle response from gateway (routed messages from SkillServer)
     */
    private void handleGatewayResponse(String responseJson) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseJson, Map.class);
            String type = (String) parsed.get("type");
            String sessionId = (String) parsed.get("sessionId");

            log.debug("Received message from gateway: type={}, sessionId={}", type, sessionId);

            switch (type) {
                case OpenCodeMessage.AgentExecuteRequest.TYPE ->
                        handleExecuteRequest(sessionId, responseJson);
                case OpenCodeMessage.AgentCancelRequest.TYPE ->
                        handleCancelRequest(sessionId);
                case OpenCodeMessage.AgentToolResultRequest.TYPE ->
                        handleToolResult(sessionId, responseJson);
                case "session.registered" ->
                        log.info("Agent registration confirmed: {}", responseJson);
                default ->
                        log.debug("Unhandled message type: {}", type);
            }

        } catch (Exception e) {
            log.error("Error handling gateway response: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle execute request from SkillServer (routed via Gateway)
     */
    private void handleExecuteRequest(String sessionId, String requestJson) {
        try {
            OpenCodeMessage.AgentExecuteRequest request =
                    objectMapper.readValue(requestJson, OpenCodeMessage.AgentExecuteRequest.class);

            log.info("Processing execute request from gateway: sessionId={}", sessionId);

            // Create response sink for this session
            Sinks.Many<WebSocketMessage.WebSocketMessageBase> responseSink =
                    Sinks.many().unicast().onBackpressureBuffer();
            activeSessions.put(sessionId, responseSink);

            // Forward to external agent
            externalAgentClient.execute(sessionId, request)
                    .timeout(Duration.ofMillis(gatewayProperties.getRequestTimeout()))
                    .subscribe(
                            response -> {
                                // Send response back through gateway
                                sendResponseToGateway(sessionId, response);
                                responseSink.tryEmitNext(response);
                            },
                            error -> {
                                log.error("External agent error: sessionId={}, error={}", sessionId, error.getMessage());
                                handleSessionError(sessionId, error);
                            },
                            () -> {
                                log.info("External agent completed: sessionId={}", sessionId);
                                completeSession(sessionId);
                            }
                    );

        } catch (Exception e) {
            log.error("Error handling execute request: {}", e.getMessage(), e);
            handleSessionError(sessionId, e);
        }
    }

    /**
     * Handle cancel request
     */
    private void handleCancelRequest(String sessionId) {
        log.info("Cancelling session: {}", sessionId);
        externalAgentClient.cancel(sessionId);
        completeSession(sessionId);
    }

    /**
     * Handle tool result submission
     */
    private void handleToolResult(String sessionId, String requestJson) {
        log.info("Received tool results for session: {}", sessionId);
        // Forward to external agent if needed
    }

    /**
     * Send response back through gateway to SkillServer
     */
    private void sendResponseToGateway(String sessionId, WebSocketMessage.WebSocketMessageBase response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            gatewayClient.sendMessage(sessionId, json);
            log.debug("Sent response to gateway: sessionId={}, type={}", sessionId, response.getType());
        } catch (Exception e) {
            log.error("Error sending response to gateway: {}", e.getMessage());
        }
    }

    /**
     * Handle session error
     */
    private void handleSessionError(String sessionId, Throwable error) {
        Sinks.Many<WebSocketMessage.WebSocketMessageBase> sink = activeSessions.get(sessionId);
        if (sink != null) {
            OpenCodeMessage.AgentErrorResponse errorResponse =
                    OpenCodeMessage.AgentErrorResponse.builder()
                            .sessionId(sessionId)
                            .payload(OpenCodeMessage.AgentErrorResponse.Payload.builder()
                                    .code("AGENT_ERROR")
                                    .message(error.getMessage())
                                    .build())
                            .build();

            sendResponseToGateway(sessionId, errorResponse);
            sink.tryEmitError(error);
        }
        activeSessions.remove(sessionId);
    }

    /**
     * Complete a session
     */
    private void completeSession(String sessionId) {
        Sinks.Many<WebSocketMessage.WebSocketMessageBase> sink = activeSessions.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        gatewayClient.unregisterSession(sessionId);
        log.debug("Session completed: {}", sessionId);
    }

    /**
     * Convert sink types
     */
    @SuppressWarnings("unchecked")
    private Sinks.Many<WebSocketMessage.WebSocketMessageBase> convertSink(Sinks.Many<String> stringSink) {
        // This is a workaround - in practice you'd handle this differently
        Sinks.Many<WebSocketMessage.WebSocketMessageBase> result =
                Sinks.many().unicast().onBackpressureBuffer();

        stringSink.asFlux()
                .subscribe(json -> {
                    try {
                        WebSocketMessage.WebSocketMessageBase msg =
                                objectMapper.readValue(json, WebSocketMessage.WebSocketMessageBase.class);
                        result.tryEmitNext(msg);
                    } catch (Exception e) {
                        log.error("Error converting message: {}", e.getMessage());
                    }
                });

        return result;
    }

    @PreDestroy
    public void destroy() {
        activeSessions.values().forEach(sink -> {
            try {
                sink.tryEmitComplete();
            } catch (Exception e) {
                log.debug("Error completing sink: {}", e.getMessage());
            }
        });
        activeSessions.clear();
    }
}