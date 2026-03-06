package com.aiplatform.websocket;

import com.aiplatform.agent.AgentAdapter;
import com.aiplatform.agent.AgentAdapterRegistry;
import com.aiplatform.agent.AgentExecutionContext;
import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.routing.ExecutionRouter;
import com.aiplatform.service.SessionService;
import com.aiplatform.service.SkillExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Main WebSocket handler for skill server.
 * Entry point for CUI/IM clients.
 * Routes requests through: SkillServer -> SkillGateway -> WeLinkPlugin -> Agent
 * Supports both skill.execute and agent.execute protocols.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final SkillExecutionService skillExecutionService;
    private final ExecutionRouter executionRouter;

    // Active sessions: sessionId -> message sink
    private final Map<String, Sinks.Many<String>> activeSessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        log.info("WebSocket connection established: {}", sessionId);

        // Create sink for outgoing messages
        Sinks.Many<String> outputSink = Sinks.many().unicast().onBackpressureBuffer();
        activeSessions.put(sessionId, outputSink);

        // Handle incoming messages
        Flux<String> inputMessages = session.receive()
                .map(msg -> msg.getPayloadAsText())
                .doOnNext(msg -> handleMessage(sessionId, msg, outputSink))
                .doOnError(e -> log.error("Error receiving message: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());

        // Heartbeat
        Flux<String> heartbeat = Flux.interval(Duration.ofSeconds(30))
                .map(i -> createHeartbeat())
                .doOnSubscribe(s -> log.debug("Started heartbeat for session: {}", sessionId));

        // Outgoing messages
        Flux<String> outputMessages = Flux.merge(
                outputSink.asFlux(),
                heartbeat
        );

        return session.send(
                outputMessages
                        .map(session::textMessage)
                        .doOnNext(msg -> log.debug("Sending message to session: {}", sessionId))
        )
                .doFinally(signal -> {
                    log.info("WebSocket connection closed: {}, signal: {}", sessionId, signal);
                    cleanup(sessionId);
                });
    }

    /**
     * Handle incoming WebSocket message
     */
    private void handleMessage(String wsSessionId, String message, Sinks.Many<String> outputSink) {
        try {
            log.debug("Received message: {}", message);

            WebSocketMessage.BaseMessage baseMessage = objectMapper.readValue(message, WebSocketMessage.BaseMessage.class);
            String type = baseMessage.getType();

            switch (type) {
                case WebSocketMessage.SkillExecuteRequest.TYPE -> handleSkillExecute(wsSessionId, message, outputSink);
                case WebSocketMessage.SkillPauseRequest.TYPE -> handleSkillPause(message, outputSink);
                case WebSocketMessage.SkillResumeRequest.TYPE -> handleSkillResume(message, outputSink);
                case WebSocketMessage.SkillCancelRequest.TYPE -> handleSkillCancel(message, outputSink);
                // OpenCode agent protocol
                case OpenCodeMessage.AgentExecuteRequest.TYPE -> handleAgentExecute(wsSessionId, message, outputSink);
                case OpenCodeMessage.AgentCancelRequest.TYPE -> handleAgentCancel(message, outputSink);
                case OpenCodeMessage.AgentToolResultRequest.TYPE -> handleAgentToolResult(message, outputSink);
                case WebSocketMessage.Heartbeat.TYPE -> handleHeartbeat(outputSink);
                default -> sendError(outputSink, null, "UNKNOWN_TYPE", "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendError(outputSink, null, "PARSE_ERROR", "Failed to parse message: " + e.getMessage());
        }
    }

    /**
     * Handle skill execute request (legacy protocol)
     */
    private void handleSkillExecute(String wsSessionId, String message, Sinks.Many<String> outputSink) {
        try {
            WebSocketMessage.SkillExecuteRequest request = objectMapper.readValue(
                    message,
                    WebSocketMessage.SkillExecuteRequest.class
            );

            String sessionId = (request.getSessionId() != null && !request.getSessionId().isEmpty())
                    ? request.getSessionId()
                    : UUID.randomUUID().toString();

            log.info("Executing skill: sessionId={}, skillId={}", sessionId, request.getPayload().getSkillId());

            // Build execution context
            AgentExecutionContext context = AgentExecutionContext.builder()
                    .wsSessionId(wsSessionId)
                    .sessionId(sessionId)
                    .protocolType(WebSocketMessage.SkillExecuteRequest.TYPE)
                    .skillRequest(request)
                    .streaming(true)
                    .channel(request.getPayload().getChannel())
                    .build();

            // Route through gateway or execute locally
            executeWithContext(context, outputSink);

        } catch (Exception e) {
            log.error("Error executing skill: {}", e.getMessage(), e);
            sendError(outputSink, null, "EXECUTION_ERROR", "Failed to execute skill: " + e.getMessage());
        }
    }

    /**
     * Handle agent execute request (OpenCode protocol)
     */
    private void handleAgentExecute(String wsSessionId, String message, Sinks.Many<String> outputSink) {
        try {
            OpenCodeMessage.AgentExecuteRequest request = objectMapper.readValue(
                    message,
                    OpenCodeMessage.AgentExecuteRequest.class
            );

            String sessionId = (request.getSessionId() != null && !request.getSessionId().isEmpty())
                    ? request.getSessionId()
                    : UUID.randomUUID().toString();

            boolean streaming = request.getPayload().isStream();
            String skillId = request.getPayload().getSkillId();
            String skillName = request.getPayload().getSkillName();

            log.info("Executing agent: sessionId={}, skillId={}, skillName={}, streaming={}",
                    sessionId, skillId, skillName, streaming);

            // Build execution context
            AgentExecutionContext context = AgentExecutionContext.builder()
                    .wsSessionId(wsSessionId)
                    .sessionId(sessionId)
                    .protocolType(OpenCodeMessage.AgentExecuteRequest.TYPE)
                    .agentRequest(request)
                    .streaming(streaming)
                    .channel(request.getPayload().getChannel())
                    .build();

            // Route through gateway or execute locally
            executeWithContext(context, outputSink);

        } catch (Exception e) {
            log.error("Error executing agent: {}", e.getMessage(), e);
            sendAgentError(outputSink, null, "EXECUTION_ERROR", "Failed to execute agent: " + e.getMessage());
        }
    }

    /**
     * Execute with context using ExecutionRouter
     */
    private void executeWithContext(AgentExecutionContext context, Sinks.Many<String> outputSink) {
        log.info("Routing execution: sessionId={}, gatewayAvailable={}",
                context.getSessionId(), executionRouter.isGatewayAvailable());

        executionRouter.execute(context)
                .subscribe(
                        response -> {
                            try {
                                String json = objectMapper.writeValueAsString(response);
                                outputSink.tryEmitNext(json);
                            } catch (Exception e) {
                                log.error("Error serializing response: {}", e.getMessage());
                            }
                        },
                        error -> {
                            log.error("Execution failed: {}", error.getMessage());
                            if (context.getProtocolType().startsWith("agent.")) {
                                sendAgentError(outputSink, context.getSessionId(), "EXECUTION_ERROR", error.getMessage());
                            } else {
                                sendError(outputSink, context.getSessionId(), "EXECUTION_ERROR", error.getMessage());
                            }
                        },
                        () -> log.info("Execution completed: sessionId={}", context.getSessionId())
                );
    }

    /**
     * Handle skill pause request
     */
    private void handleSkillPause(String message, Sinks.Many<String> outputSink) {
        try {
            WebSocketMessage.SkillPauseRequest request = objectMapper.readValue(
                    message,
                    WebSocketMessage.SkillPauseRequest.class
            );
            skillExecutionService.pauseSkill(request.getSessionId());
            log.info("Skill paused: sessionId={}", request.getSessionId());
        } catch (Exception e) {
            log.error("Error pausing skill: {}", e.getMessage());
            sendError(outputSink, null, "PAUSE_ERROR", e.getMessage());
        }
    }

    /**
     * Handle skill resume request
     */
    private void handleSkillResume(String message, Sinks.Many<String> outputSink) {
        try {
            WebSocketMessage.SkillResumeRequest request = objectMapper.readValue(
                    message,
                    WebSocketMessage.SkillResumeRequest.class
            );
            skillExecutionService.resumeSkill(request.getSessionId());
            log.info("Skill resumed: sessionId={}", request.getSessionId());
        } catch (Exception e) {
            log.error("Error resuming skill: {}", e.getMessage());
            sendError(outputSink, null, "RESUME_ERROR", e.getMessage());
        }
    }

    /**
     * Handle skill cancel request
     */
    private void handleSkillCancel(String message, Sinks.Many<String> outputSink) {
        try {
            WebSocketMessage.SkillCancelRequest request = objectMapper.readValue(
                    message,
                    WebSocketMessage.SkillCancelRequest.class
            );
            skillExecutionService.cancelSkill(request.getSessionId());
            // Cancel via router
            executionRouter.cancel(request.getSessionId());
            log.info("Skill cancelled: sessionId={}", request.getSessionId());
        } catch (Exception e) {
            log.error("Error cancelling skill: {}", e.getMessage());
            sendError(outputSink, null, "CANCEL_ERROR", e.getMessage());
        }
    }

    /**
     * Handle agent cancel request
     */
    private void handleAgentCancel(String message, Sinks.Many<String> outputSink) {
        try {
            OpenCodeMessage.AgentCancelRequest request = objectMapper.readValue(
                    message,
                    OpenCodeMessage.AgentCancelRequest.class
            );
            executionRouter.cancel(request.getSessionId());
            log.info("Agent cancelled: sessionId={}", request.getSessionId());
        } catch (Exception e) {
            log.error("Error cancelling agent: {}", e.getMessage());
            sendAgentError(outputSink, null, "CANCEL_ERROR", e.getMessage());
        }
    }

    /**
     * Handle agent tool result submission
     */
    private void handleAgentToolResult(String message, Sinks.Many<String> outputSink) {
        try {
            OpenCodeMessage.AgentToolResultRequest request = objectMapper.readValue(
                    message,
                    OpenCodeMessage.AgentToolResultRequest.class
            );
            // In real implementation, this would submit tool results back to the agent
            log.info("Received tool results for session: {}", request.getSessionId());
            // TODO: Implement tool result handling
        } catch (Exception e) {
            log.error("Error handling tool results: {}", e.getMessage());
            sendAgentError(outputSink, null, "TOOL_RESULT_ERROR", e.getMessage());
        }
    }

    /**
     * Handle heartbeat
     */
    private void handleHeartbeat(Sinks.Many<String> outputSink) {
        try {
            String response = objectMapper.writeValueAsString(
                    WebSocketMessage.Heartbeat.builder()
                            .timestamp(System.currentTimeMillis())
                            .build()
            );
            outputSink.tryEmitNext(response);
        } catch (Exception e) {
            log.error("Error sending heartbeat: {}", e.getMessage());
        }
    }

    /**
     * Send progress update
     */
    private void sendProgress(Sinks.Many<String> outputSink, String sessionId,
                              WebSocketMessage.SkillProgressResponse.Payload progress) {
        try {
            String message = objectMapper.writeValueAsString(
                    WebSocketMessage.SkillProgressResponse.builder()
                            .sessionId(sessionId)
                            .payload(progress)
                            .build()
            );
            outputSink.tryEmitNext(message);
        } catch (Exception e) {
            log.error("Error sending progress: {}", e.getMessage());
        }
    }

    /**
     * Send error response (skill protocol)
     */
    private void sendError(Sinks.Many<String> outputSink, String sessionId, String code, String message) {
        try {
            String errorMessage = objectMapper.writeValueAsString(
                    WebSocketMessage.SkillErrorResponse.builder()
                            .sessionId(sessionId)
                            .payload(WebSocketMessage.SkillErrorResponse.Payload.builder()
                                    .code(code)
                                    .message(message)
                                    .build())
                            .build()
            );
            outputSink.tryEmitNext(errorMessage);
        } catch (Exception e) {
            log.error("Error sending error response: {}", e.getMessage());
        }
    }

    /**
     * Send agent error response (OpenCode protocol)
     */
    private void sendAgentError(Sinks.Many<String> outputSink, String sessionId, String code, String message) {
        try {
            String errorMessage = objectMapper.writeValueAsString(
                    OpenCodeMessage.AgentErrorResponse.builder()
                            .sessionId(sessionId)
                            .payload(OpenCodeMessage.AgentErrorResponse.Payload.builder()
                                    .code(code)
                                    .message(message)
                                    .build())
                            .build()
            );
            outputSink.tryEmitNext(errorMessage);
        } catch (Exception e) {
            log.error("Error sending agent error response: {}", e.getMessage());
        }
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

    /**
     * Cleanup session resources
     */
    private void cleanup(String sessionId) {
        Sinks.Many<String> sink = activeSessions.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        sessionService.deactivateSession(sessionId);
    }

    /**
     * Send message to a specific session
     */
    public void sendToSession(String sessionId, String message) {
        Sinks.Many<String> sink = activeSessions.get(sessionId);
        if (sink != null) {
            sink.tryEmitNext(message);
        }
    }

    /**
     * Check if session is active
     */
    public boolean isSessionActive(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}