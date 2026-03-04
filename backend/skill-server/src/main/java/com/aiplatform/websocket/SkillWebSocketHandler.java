package com.aiplatform.websocket;

import com.aiplatform.dto.WebSocketMessage;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main WebSocket handler for skill server.
 * Handles WebSocket connections, message routing, and skill execution.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final SkillExecutionService skillExecutionService;

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
                case WebSocketMessage.Heartbeat.TYPE -> handleHeartbeat(outputSink);
                default -> sendError(outputSink, null, "UNKNOWN_TYPE", "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendError(outputSink, null, "PARSE_ERROR", "Failed to parse message: " + e.getMessage());
        }
    }

    /**
     * Handle skill execute request
     */
    private void handleSkillExecute(String wsSessionId, String message, Sinks.Many<String> outputSink) {
        try {
            WebSocketMessage.SkillExecuteRequest request = objectMapper.readValue(
                    message,
                    WebSocketMessage.SkillExecuteRequest.class
            );

            String sessionId = request.getSessionId();
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = java.util.UUID.randomUUID().toString();
            }

            log.info("Executing skill: sessionId={}, skillId={}", sessionId, request.getPayload().getSkillId());

            // Execute skill asynchronously
            skillExecutionService.executeSkill(sessionId, request.getPayload())
                    .subscribe(
                            progress -> sendProgress(outputSink, sessionId, progress),
                            error -> {
                                log.error("Skill execution failed: {}", error.getMessage());
                                sendError(outputSink, sessionId, "EXECUTION_ERROR", error.getMessage());
                            },
                            () -> log.info("Skill execution completed: sessionId={}", sessionId)
                    );

        } catch (Exception e) {
            log.error("Error executing skill: {}", e.getMessage(), e);
            sendError(outputSink, null, "EXECUTION_ERROR", "Failed to execute skill: " + e.getMessage());
        }
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
            log.info("Skill cancelled: sessionId={}", request.getSessionId());
        } catch (Exception e) {
            log.error("Error cancelling skill: {}", e.getMessage());
            sendError(outputSink, null, "CANCEL_ERROR", e.getMessage());
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
     * Send error response
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