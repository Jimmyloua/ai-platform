package com.aiplatform.agent;

import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Execution context for agent adapters.
 * Contains all necessary information for executing an agent request.
 */
@Data
@Builder
public class AgentExecutionContext {

    /**
     * WebSocket session ID
     */
    private String wsSessionId;

    /**
     * Skill session ID (may be different from ws session)
     */
    private String sessionId;

    /**
     * Protocol type being used (skill.execute, agent.execute, etc.)
     */
    private String protocolType;

    /**
     * Raw message payload
     */
    private Map<String, Object> rawPayload;

    /**
     * Parsed skill execute request (for DEFAULT protocol)
     */
    private WebSocketMessage.SkillExecuteRequest skillRequest;

    /**
     * Parsed OpenCode agent request (for OPENCODE protocol)
     */
    private OpenCodeMessage.AgentExecuteRequest agentRequest;

    /**
     * Whether streaming is requested
     */
    private boolean streaming;

    /**
     * User ID making the request
     */
    private String userId;

    /**
     * Channel/source of the request
     */
    private String channel;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Get skill ID from either skill or agent request
     */
    public String getSkillId() {
        if (skillRequest != null && skillRequest.getPayload() != null) {
            return skillRequest.getPayload().getSkillId();
        }
        if (agentRequest != null && agentRequest.getPayload() != null) {
            return agentRequest.getPayload().getSkillId();
        }
        return null;
    }

    /**
     * Get skill name from either skill or agent request
     */
    public String getSkillName() {
        if (skillRequest != null && skillRequest.getPayload() != null) {
            return skillRequest.getPayload().getSkillName();
        }
        if (agentRequest != null && agentRequest.getPayload() != null) {
            return agentRequest.getPayload().getSkillName();
        }
        return null;
    }

    /**
     * Get input data from either request type
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInput() {
        if (skillRequest != null && skillRequest.getPayload() != null) {
            return skillRequest.getPayload().getInput();
        }
        if (agentRequest != null && agentRequest.getPayload() != null) {
            // For agent requests, extract the last user message as input
            var messages = agentRequest.getPayload().getMessages();
            if (messages != null && !messages.isEmpty()) {
                var lastMessage = messages.get(messages.size() - 1);
                if (lastMessage.getContent() != null) {
                    return Map.of("message", lastMessage.getContent());
                }
            }
        }
        return Map.of();
    }
}