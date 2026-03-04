package com.aiplatform.service;

import com.aiplatform.config.GatewayClientProperties;
import com.aiplatform.config.WeLinkProperties;
import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.gateway.GatewayClient;
import com.aiplatform.model.WeLinkMessage;
import com.aiplatform.protocol.OpenCodeProtocolAdapter;
import com.aiplatform.protocol.ProtocolAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for invoking skills through the gateway or direct HTTP fallback.
 * Implements dual-mode: gateway with AK/SK as primary, direct HTTP fallback if unavailable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayInvocationService {

    private final GatewayClientProperties gatewayProperties;
    private final WeLinkProperties weLinkProperties;
    private final GatewayClient gatewayClient;
    private final WeLinkMessageService messageService;
    private final ObjectMapper objectMapper;

    private final ProtocolAdapter protocolAdapter = new OpenCodeProtocolAdapter();
    private WebClient webClient;

    // Active sessions for accumulating streaming chunks
    private final Map<String, StreamingAccumulator> streamingSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
                .baseUrl(weLinkProperties.getSkillServer().getUrl())
                .build();
    }

    /**
     * Invoke a skill from a WeLink message.
     * Routes through gateway if available, falls back to direct HTTP.
     */
    public void invokeSkill(WeLinkMessage welinkMessage) {
        if (gatewayProperties.isEnabled() && gatewayClient.isConnected()) {
            invokeViaGateway(welinkMessage);
        } else if (gatewayProperties.isFallbackEnabled()) {
            log.info("Gateway unavailable, falling back to direct HTTP");
            invokeViaHttp(welinkMessage);
        } else {
            log.error("Gateway unavailable and fallback disabled");
            sendErrorMessage(welinkMessage, "Service temporarily unavailable. Please try again later.");
        }
    }

    /**
     * Invoke skill via gateway with streaming support
     */
    private void invokeViaGateway(WeLinkMessage welinkMessage) {
        try {
            String sessionId = UUID.randomUUID().toString();
            boolean streaming = gatewayProperties.getStreaming().isEnabled();

            log.info("Invoking skill via gateway: sessionId={}, msgId={}, streaming={}",
                    sessionId, welinkMessage.getMsgId(), streaming);

            // Adapt WeLink message to gateway protocol
            WebSocketMessage.WebSocketMessageBase gatewayRequest = protocolAdapter.adaptToGateway(welinkMessage, streaming);

            // Override session ID
            if (gatewayRequest instanceof OpenCodeMessage.AgentExecuteRequest) {
                ((OpenCodeMessage.AgentExecuteRequest) gatewayRequest).setSessionId(sessionId);
            }

            // Register for responses
            Sinks.Many<String> responseSink = gatewayClient.registerSession(sessionId);

            // Initialize streaming accumulator if streaming is enabled
            if (streaming) {
                streamingSessions.put(sessionId, new StreamingAccumulator(welinkMessage));
            }

            // Send request to gateway
            String requestJson = objectMapper.writeValueAsString(gatewayRequest);
            gatewayClient.sendMessage(sessionId, requestJson);

            // Handle responses
            responseSink.asFlux()
                    .timeout(Duration.ofMillis(gatewayProperties.getStreaming().getChunkTimeout()))
                    .subscribe(
                            response -> handleGatewayResponse(sessionId, welinkMessage, response),
                            error -> {
                                log.error("Gateway response error: {}", error.getMessage());
                                handleGatewayError(sessionId, welinkMessage, error);
                            },
                            () -> {
                                log.info("Gateway session completed: sessionId={}", sessionId);
                                completeSession(sessionId, welinkMessage);
                            }
                    );

        } catch (Exception e) {
            log.error("Error invoking skill via gateway: {}", e.getMessage(), e);
            sendErrorMessage(welinkMessage, "Failed to process your request. Please try again.");
        }
    }

    /**
     * Invoke skill via direct HTTP (fallback)
     */
    private void invokeViaHttp(WeLinkMessage welinkMessage) {
        try {
            String sessionId = UUID.randomUUID().toString();

            // Build skill execution request
            Map<String, Object> request = Map.of(
                    "sessionId", sessionId,
                    "channel", "im",
                    "userId", welinkMessage.getFromUserId(),
                    "input", Map.of(
                            "message", welinkMessage.getContent(),
                            "chatType", welinkMessage.getChatType(),
                            "groupId", welinkMessage.getGroupId() != null ? welinkMessage.getGroupId() : "",
                            "msgId", welinkMessage.getMsgId()
                    ),
                    "metadata", Map.of(
                            "platform", "welink",
                            "tenantId", welinkMessage.getTenantId(),
                            "correlationId", welinkMessage.getCorrelationId() != null
                                    ? welinkMessage.getCorrelationId()
                                    : sessionId
                    )
            );

            // Send to skill server
            webClient.post()
                    .uri("/api/v1/skills/execute")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(weLinkProperties.getSkillServer().getTimeout()))
                    .subscribe(
                            response -> handleHttpResponse(welinkMessage, response),
                            error -> handleHttpError(welinkMessage, error)
                    );

            log.info("Skill invocation started via HTTP: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("Error invoking skill via HTTP: {}", e.getMessage(), e);
            sendErrorMessage(welinkMessage, "Failed to process your request. Please try again.");
        }
    }

    /**
     * Handle gateway response
     */
    private void handleGatewayResponse(String sessionId, WeLinkMessage welinkMessage, String response) {
        try {
            WebSocketMessage.WebSocketMessageBase gatewayMessage = parseGatewayMessage(response);

            if (gatewayMessage instanceof OpenCodeMessage.AgentResponseChunk) {
                // Streaming chunk
                handleStreamingChunk(sessionId, welinkMessage, (OpenCodeMessage.AgentResponseChunk) gatewayMessage);
            } else if (gatewayMessage instanceof OpenCodeMessage.AgentResultResponse) {
                // Final result
                handleAgentResult(sessionId, welinkMessage, (OpenCodeMessage.AgentResultResponse) gatewayMessage);
            } else if (gatewayMessage instanceof OpenCodeMessage.AgentErrorResponse) {
                // Error
                handleAgentError(sessionId, welinkMessage, (OpenCodeMessage.AgentErrorResponse) gatewayMessage);
            } else if (gatewayMessage instanceof WebSocketMessage.SkillProgressResponse) {
                // Legacy progress
                handleSkillProgress(welinkMessage, (WebSocketMessage.SkillProgressResponse) gatewayMessage);
            } else if (gatewayMessage instanceof WebSocketMessage.SkillResultResponse) {
                // Legacy result
                handleSkillResult(welinkMessage, (WebSocketMessage.SkillResultResponse) gatewayMessage);
            } else if (gatewayMessage instanceof WebSocketMessage.SkillErrorResponse) {
                // Legacy error
                handleSkillError(welinkMessage, (WebSocketMessage.SkillErrorResponse) gatewayMessage);
            }

        } catch (Exception e) {
            log.error("Error handling gateway response: {}", e.getMessage());
        }
    }

    /**
     * Handle streaming chunk
     */
    private void handleStreamingChunk(String sessionId, WeLinkMessage welinkMessage,
                                       OpenCodeMessage.AgentResponseChunk chunk) {
        StreamingAccumulator accumulator = streamingSessions.get(sessionId);
        if (accumulator != null) {
            accumulator.addChunk(chunk);

            // Check if complete
            if (chunk.getPayload().isDone()) {
                String fullContent = accumulator.getFullContent();
                messageService.sendTextMessage(
                        welinkMessage.getFromUserId(),
                        welinkMessage.getGroupId(),
                        fullContent
                );
                streamingSessions.remove(sessionId);
            }
        }
    }

    /**
     * Handle agent result
     */
    private void handleAgentResult(String sessionId, WeLinkMessage welinkMessage,
                                    OpenCodeMessage.AgentResultResponse result) {
        StreamingAccumulator accumulator = streamingSessions.remove(sessionId);
        String content;

        if (accumulator != null && !accumulator.isEmpty()) {
            // Use accumulated content
            content = accumulator.getFullContent();
        } else if (result.getPayload() != null && result.getPayload().getMessage() != null) {
            // Use result message
            content = result.getPayload().getMessage().getContent();
        } else {
            content = "No response generated";
        }

        messageService.sendTextMessage(
                welinkMessage.getFromUserId(),
                welinkMessage.getGroupId(),
                content
        );
    }

    /**
     * Handle agent error
     */
    private void handleAgentError(String sessionId, WeLinkMessage welinkMessage,
                                   OpenCodeMessage.AgentErrorResponse error) {
        streamingSessions.remove(sessionId);
        String errorMessage = error.getPayload() != null ? error.getPayload().getMessage() : "Unknown error";
        sendErrorMessage(welinkMessage, "Error: " + errorMessage);
    }

    /**
     * Handle skill progress (legacy)
     */
    private void handleSkillProgress(WeLinkMessage welinkMessage, WebSocketMessage.SkillProgressResponse progress) {
        // For non-streaming clients, we don't send intermediate progress
        log.debug("Skill progress: {}%", progress.getPayload().getProgress());
    }

    /**
     * Handle skill result (legacy)
     */
    private void handleSkillResult(WeLinkMessage welinkMessage, WebSocketMessage.SkillResultResponse result) {
        String content;
        if (result.getPayload() != null && result.getPayload().getResult() != null) {
            Object res = result.getPayload().getResult();
            if (res instanceof String) {
                content = (String) res;
            } else {
                content = res.toString();
            }
        } else {
            content = "No result";
        }

        messageService.sendTextMessage(
                welinkMessage.getFromUserId(),
                welinkMessage.getGroupId(),
                content
        );
    }

    /**
     * Handle skill error (legacy)
     */
    private void handleSkillError(WeLinkMessage welinkMessage, WebSocketMessage.SkillErrorResponse error) {
        String errorMessage = error.getPayload() != null ? error.getPayload().getMessage() : "Unknown error";
        sendErrorMessage(welinkMessage, errorMessage);
    }

    /**
     * Handle gateway error
     */
    private void handleGatewayError(String sessionId, WeLinkMessage welinkMessage, Throwable error) {
        streamingSessions.remove(sessionId);
        gatewayClient.unregisterSession(sessionId);

        log.error("Gateway error for session {}: {}", sessionId, error.getMessage());

        // If gateway error and fallback enabled, try HTTP
        if (gatewayProperties.isFallbackEnabled()) {
            log.info("Attempting HTTP fallback after gateway error");
            invokeViaHttp(welinkMessage);
        } else {
            sendErrorMessage(welinkMessage, "An error occurred. Please try again.");
        }
    }

    /**
     * Complete session
     */
    private void completeSession(String sessionId, WeLinkMessage welinkMessage) {
        gatewayClient.unregisterSession(sessionId);
        streamingSessions.remove(sessionId);
    }

    /**
     * Handle HTTP response
     */
    @SuppressWarnings("unchecked")
    private void handleHttpResponse(WeLinkMessage welinkMessage, Map<String, Object> response) {
        try {
            String status = (String) response.get("status");
            Object result = response.get("result");

            if ("completed".equals(status) && result != null) {
                String content;
                if (result instanceof String) {
                    content = (String) result;
                } else {
                    content = objectMapper.writeValueAsString(result);
                }

                messageService.sendTextMessage(
                        welinkMessage.getFromUserId(),
                        welinkMessage.getGroupId(),
                        content
                );
            } else if ("failed".equals(status)) {
                String errorMessage = (String) response.get("errorMessage");
                sendErrorMessage(welinkMessage, errorMessage != null ? errorMessage : "Execution failed");
            }
        } catch (Exception e) {
            log.error("Error handling HTTP response", e);
        }
    }

    /**
     * Handle HTTP error
     */
    private void handleHttpError(WeLinkMessage welinkMessage, Throwable error) {
        log.error("HTTP error for message: {}", welinkMessage.getMsgId(), error);
        sendErrorMessage(welinkMessage, "An error occurred while processing your request.");
    }

    /**
     * Parse gateway message
     */
    private WebSocketMessage.WebSocketMessageBase parseGatewayMessage(String json) throws Exception {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        String type = (String) map.get("type");

        if (type == null) {
            throw new IllegalArgumentException("Message missing type field");
        }

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
            default -> throw new IllegalArgumentException("Unknown message type: " + type);
        };
    }

    /**
     * Send error message to user
     */
    private void sendErrorMessage(WeLinkMessage welinkMessage, String errorText) {
        messageService.sendTextMessage(
                welinkMessage.getFromUserId(),
                welinkMessage.getGroupId(),
                "❌ " + errorText
        );
    }

    /**
     * Streaming accumulator for collecting chunks
     */
    private static class StreamingAccumulator {
        private final WeLinkMessage welinkMessage;
        private final List<String> chunks = new ArrayList<>();
        private final List<OpenCodeMessage.ToolCall> toolCalls = new ArrayList<>();

        public StreamingAccumulator(WeLinkMessage welinkMessage) {
            this.welinkMessage = welinkMessage;
        }

        public void addChunk(OpenCodeMessage.AgentResponseChunk chunk) {
            if (chunk.getPayload() != null && chunk.getPayload().getDelta() != null) {
                var delta = chunk.getPayload().getDelta();
                if (delta.getContent() != null) {
                    chunks.add(delta.getContent());
                }
                if (delta.getToolCalls() != null) {
                    toolCalls.addAll(delta.getToolCalls());
                }
            }
        }

        public String getFullContent() {
            return String.join("", chunks);
        }

        public List<OpenCodeMessage.ToolCall> getToolCalls() {
            return toolCalls;
        }

        public boolean isEmpty() {
            return chunks.isEmpty();
        }
    }
}