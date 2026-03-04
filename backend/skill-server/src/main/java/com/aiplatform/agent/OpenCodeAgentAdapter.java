package com.aiplatform.agent;

import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.mapper.SkillMapper;
import com.aiplatform.mapper.SessionMapper;
import com.aiplatform.model.Skill;
import com.aiplatform.model.SkillSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenCode agent adapter for agent-style protocol execution.
 * Handles OpenCode-style requests with streaming responses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenCodeAgentAdapter implements AgentAdapter {

    private final SkillMapper skillMapper;
    private final SessionMapper sessionMapper;
    private final ObjectMapper objectMapper;

    // Active executions for cancellation
    private final Map<String, Boolean> cancelledSessions = new ConcurrentHashMap<>();

    @Override
    public String getAgentType() {
        return "OPENCODE";
    }

    @Override
    public Flux<WebSocketMessage.WebSocketMessageBase> execute(AgentExecutionContext context) {
        log.info("Executing with OPENCODE adapter: sessionId={}, streaming={}",
                context.getSessionId(), context.isStreaming());

        return Flux.create(sink -> {
            try {
                // Find skill if skillId or skillName provided
                Skill skill = findSkill(context);
                if (skill == null && context.getSkillId() == null && context.getSkillName() == null) {
                    // Allow execution without skill (direct agent mode)
                    log.info("No skill specified, executing in direct agent mode");
                } else if (skill == null) {
                    sink.error(new RuntimeException("Skill not found"));
                    return;
                }

                // Create session
                SkillSession session = createSession(context.getSessionId(), skill, context);
                sessionMapper.insert(session);

                // Execute asynchronously
                executeAgentInternal(context, skill, sink);

            } catch (Exception e) {
                log.error("Error starting agent execution: {}", e.getMessage(), e);
                sink.error(e);
            }
        });
    }

    @Override
    public boolean supports(String protocolType) {
        return protocolType != null && protocolType.startsWith("agent.");
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public void cancel(String sessionId) {
        cancelledSessions.put(sessionId, true);
    }

    /**
     * Internal agent execution
     */
    @Async("skillExecutor")
    protected void executeAgentInternal(AgentExecutionContext context, Skill skill,
                                         FluxSink<WebSocketMessage.WebSocketMessageBase> sink) {
        String sessionId = context.getSessionId();
        long startTime = System.currentTimeMillis();
        int chunkId = 0;

        try {
            // Update session status
            updateSessionStatus(sessionId, SkillSession.Status.RUNNING);

            // Get messages from agent request
            List<OpenCodeMessage.Message> messages = new ArrayList<>();
            if (context.getAgentRequest() != null && context.getAgentRequest().getPayload() != null) {
                var payload = context.getAgentRequest().getPayload();
                if (payload.getMessages() != null) {
                    messages.addAll(payload.getMessages());
                }
            }

            // Simulate agent processing with streaming chunks
            // In real implementation, this would call an actual AI agent
            String responseContent = generateAgentResponse(messages, skill);

            if (context.isStreaming()) {
                // Stream response in chunks
                chunkId = streamResponse(sessionId, responseContent, sink, chunkId);
            } else {
                // Send complete response
                sendCompleteResponse(sessionId, responseContent, sink);
            }

            // Complete session
            long duration = System.currentTimeMillis() - startTime;
            sessionMapper.completeSession(sessionId, objectMapper.writeValueAsString(Map.of(
                    "response", responseContent,
                    "durationMs", duration
            )), LocalDateTime.now());

            // Send final result
            sendAgentResult(sessionId, responseContent, duration, sink);

        } catch (Exception e) {
            log.error("Agent execution error: {}", e.getMessage(), e);
            sessionMapper.failSession(sessionId, e.getMessage(), LocalDateTime.now());
            sink.next(createAgentError(sessionId, "EXECUTION_ERROR", e.getMessage()));
            sink.error(e);
        } finally {
            cancelledSessions.remove(sessionId);
        }
    }

    /**
     * Stream response in chunks
     */
    private int streamResponse(String sessionId, String content,
                                FluxSink<WebSocketMessage.WebSocketMessageBase> sink, int chunkId) {
        // Split content into chunks (simulate streaming)
        int chunkSize = 20; // characters per chunk
        int length = content.length();

        for (int i = 0; i < length; i += chunkSize) {
            // Check for cancellation
            if (cancelledSessions.containsKey(sessionId)) {
                updateSessionStatus(sessionId, SkillSession.Status.CANCELLED);
                sink.complete();
                return chunkId;
            }

            int end = Math.min(i + chunkSize, length);
            String chunkContent = content.substring(i, end);
            boolean isDone = end >= length;

            sink.next(createChunkMessage(sessionId, chunkId++, chunkContent, isDone));

            // Simulate processing delay
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return chunkId;
    }

    /**
     * Send complete (non-streaming) response
     */
    private void sendCompleteResponse(String sessionId, String content,
                                       FluxSink<WebSocketMessage.WebSocketMessageBase> sink) {
        sink.next(OpenCodeMessage.AgentResultResponse.builder()
                .sessionId(sessionId)
                .payload(OpenCodeMessage.AgentResultResponse.Payload.builder()
                        .message(OpenCodeMessage.Message.assistant(content))
                        .build())
                .build());
    }

    /**
     * Send agent result
     */
    private void sendAgentResult(String sessionId, String content, long duration,
                                  FluxSink<WebSocketMessage.WebSocketMessageBase> sink) {
        sink.next(OpenCodeMessage.AgentResultResponse.builder()
                .sessionId(sessionId)
                .payload(OpenCodeMessage.AgentResultResponse.Payload.builder()
                        .message(OpenCodeMessage.Message.assistant(content))
                        .usage(OpenCodeMessage.AgentResultResponse.Payload.Usage.builder()
                                .inputTokens(100) // Placeholder
                                .outputTokens(content.length() / 4) // Rough estimate
                                .durationMs(duration)
                                .build())
                        .build())
                .build());
        sink.complete();
    }

    /**
     * Generate agent response (placeholder implementation)
     */
    private String generateAgentResponse(List<OpenCodeMessage.Message> messages, Skill skill) {
        // In real implementation, this would call an AI model or skill endpoint
        StringBuilder response = new StringBuilder();

        if (skill != null) {
            response.append("Executed skill: ").append(skill.getName()).append("\n\n");
        }

        // Simple echo with context
        if (!messages.isEmpty()) {
            var lastMessage = messages.get(messages.size() - 1);
            response.append("Response to: ").append(lastMessage.getContent());
        } else {
            response.append("No messages provided");
        }

        return response.toString();
    }

    /**
     * Create chunk message for streaming
     */
    private WebSocketMessage.WebSocketMessageBase createChunkMessage(String sessionId, int chunkId,
                                                                        String content, boolean isDone) {
        return OpenCodeMessage.AgentResponseChunk.builder()
                .sessionId(sessionId)
                .payload(OpenCodeMessage.AgentResponseChunk.Payload.builder()
                        .chunkId(chunkId)
                        .delta(OpenCodeMessage.MessageDelta.builder()
                                .content(content)
                                .build())
                        .done(isDone)
                        .finishReason(isDone ? "stop" : null)
                        .build())
                .build();
    }

    /**
     * Create agent error message
     */
    private WebSocketMessage.WebSocketMessageBase createAgentError(String sessionId, String code, String message) {
        return OpenCodeMessage.AgentErrorResponse.builder()
                .sessionId(sessionId)
                .payload(OpenCodeMessage.AgentErrorResponse.Payload.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * Find skill by ID or name
     */
    private Skill findSkill(AgentExecutionContext context) {
        String skillId = context.getSkillId();
        String skillName = context.getSkillName();

        if (skillId != null && !skillId.isEmpty()) {
            return skillMapper.findByIdAndIsActiveTrue(Long.parseLong(skillId))
                    .orElse(null);
        }
        if (skillName != null && !skillName.isEmpty()) {
            return skillMapper.findByName(skillName)
                    .orElse(null);
        }
        return null;
    }

    /**
     * Create a new session
     */
    private SkillSession createSession(String sessionId, Skill skill, AgentExecutionContext context) {
        return SkillSession.builder()
                .sessionId(sessionId)
                .skillId(skill != null ? skill.getId() : null)
                .userId(context.getUserId() != null ? context.getUserId() : "user-" + UUID.randomUUID().toString().substring(0, 8))
                .channel(context.getChannel() != null ? SkillSession.Channel.valueOf(context.getChannel().toUpperCase()) : SkillSession.Channel.CUI)
                .status(SkillSession.Status.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Update session status
     */
    private void updateSessionStatus(String sessionId, SkillSession.Status status) {
        sessionMapper.updateStatus(sessionId, status.name(), LocalDateTime.now());
    }
}