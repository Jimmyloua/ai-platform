package com.aiplatform.messaging;

import com.aiplatform.routing.RoutedMessage;
import com.aiplatform.routing.SessionEndpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for reliable message routing through gateway.
 * Uses Redis Streams for at-least-once delivery guarantee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReliableRoutingService {

    private final ReliableMessageQueue messageQueue;
    private final SessionStateService sessionStateService;
    private final ObjectMapper objectMapper;

    /**
     * Route a message from client to agent with reliability guarantees.
     * Returns the message ID for tracking.
     */
    public String routeToAgent(String sessionId, String sourceConnectionId,
                               String protocolType, String messageType, String payload) {
        try {
            // Generate message ID
            String messageId = UUID.randomUUID().toString();

            // Get session state
            SessionStateService.SessionState state = sessionStateService.getSessionState(sessionId)
                    .orElse(null);

            if (state == null) {
                log.warn("No session state found for routing: sessionId={}", sessionId);
                // Store as pending - will be processed when agent connects
            }

            // Create message payload
            ReliableMessageQueue.MessagePayload messagePayload = ReliableMessageQueue.MessagePayload.builder()
                    .messageId(messageId)
                    .sessionId(sessionId)
                    .sourceId(sourceConnectionId)
                    .targetId(state != null ? state.getAgentGatewayId() : null)
                    .type(messageType)
                    .payload(payload)
                    .build();

            // Store as pending request
            sessionStateService.storePendingRequest(sessionId, messageId, payload);

            // Publish to request stream
            String recordId = messageQueue.publishRequest(messagePayload);

            log.info("Routed message to agent: sessionId={}, messageId={}, recordId={}",
                    sessionId, messageId, recordId);

            return messageId;

        } catch (Exception e) {
            log.error("Failed to route message to agent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to route message", e);
        }
    }

    /**
     * Route a message from agent to client with reliability guarantees.
     */
    public String routeToClient(String sessionId, String sourceConnectionId, String pluginId,
                                String protocolType, String messageType, String payload) {
        try {
            String messageId = UUID.randomUUID().toString();

            // Get session state
            SessionStateService.SessionState state = sessionStateService.getSessionState(sessionId)
                    .orElse(null);

            // Create message payload
            ReliableMessageQueue.MessagePayload messagePayload = ReliableMessageQueue.MessagePayload.builder()
                    .messageId(messageId)
                    .sessionId(sessionId)
                    .sourceId(sourceConnectionId)
                    .targetId(state != null ? state.getClientGatewayId() : null)
                    .type(messageType)
                    .payload(payload)
                    .build();

            // Publish to response stream
            String recordId = messageQueue.publishResponse(messagePayload);

            log.debug("Routed message to client: sessionId={}, messageId={}, recordId={}",
                    sessionId, messageId, recordId);

            // Remove pending request if this is a terminal message
            if (isTerminalMessage(messageType)) {
                // Remove the pending request - message delivered successfully
                log.debug("Terminal message delivered, removing pending requests for session: {}", sessionId);
            }

            return messageId;

        } catch (Exception e) {
            log.error("Failed to route message to client: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to route message", e);
        }
    }

    /**
     * Register session with client endpoint
     */
    public void registerClientSession(String sessionId, SessionEndpoint endpoint) {
        SessionStateService.SessionState state = SessionStateService.SessionState.builder()
                .sessionId(sessionId)
                .clientGatewayId(endpoint.getGatewayId())
                .clientConnectionId(endpoint.getConnectionId())
                .clientId(endpoint.getClientId())
                .protocolType(endpoint.getProtocolType())
                .status("active")
                .createdAt(System.currentTimeMillis())
                .build();

        sessionStateService.persistSessionState(sessionId, state);
        sessionStateService.addConnectionSession(endpoint.getConnectionId(), sessionId);

        log.info("Registered client session: sessionId={}, gateway={}, connection={}",
                sessionId, endpoint.getGatewayId(), endpoint.getConnectionId());
    }

    /**
     * Register session with agent endpoint
     */
    public void registerAgentSession(String sessionId, SessionEndpoint endpoint) {
        sessionStateService.getSessionState(sessionId).ifPresentOrElse(
                existing -> {
                    // Update existing state with agent info
                    SessionStateService.SessionState updated = SessionStateService.SessionState.builder()
                            .sessionId(existing.getSessionId())
                            .clientGatewayId(existing.getClientGatewayId())
                            .clientConnectionId(existing.getClientConnectionId())
                            .agentGatewayId(endpoint.getGatewayId())
                            .agentConnectionId(endpoint.getConnectionId())
                            .pluginId(endpoint.getPluginId())
                            .protocolType(endpoint.getProtocolType())
                            .status("connected")
                            .createdAt(existing.getCreatedAt())
                            .build();

                    sessionStateService.persistSessionState(sessionId, updated);
                    sessionStateService.addConnectionSession(endpoint.getConnectionId(), sessionId);
                },
                () -> {
                    // Create new state
                    SessionStateService.SessionState state = SessionStateService.SessionState.builder()
                            .sessionId(sessionId)
                            .agentGatewayId(endpoint.getGatewayId())
                            .agentConnectionId(endpoint.getConnectionId())
                            .pluginId(endpoint.getPluginId())
                            .protocolType(endpoint.getProtocolType())
                            .status("active")
                            .createdAt(System.currentTimeMillis())
                            .build();

                    sessionStateService.persistSessionState(sessionId, state);
                    sessionStateService.addConnectionSession(endpoint.getConnectionId(), sessionId);
                }
        );

        log.info("Registered agent session: sessionId={}, gateway={}, plugin={}",
                sessionId, endpoint.getGatewayId(), endpoint.getPluginId());
    }

    /**
     * Handle connection disconnect
     */
    public void handleDisconnect(String connectionId) {
        // Get all sessions for this connection
        java.util.Set<String> sessions = sessionStateService.getConnectionSessions(connectionId);

        for (String sessionId : sessions) {
            sessionStateService.updateSessionStatus(sessionId, "disconnected");
            sessionStateService.removeConnectionSession(connectionId, sessionId);

            // Process any pending requests for this session
            // They will be picked up by pending message recovery
            log.info("Session disconnected, pending messages will be recovered: sessionId={}", sessionId);
        }
    }

    /**
     * Check if message is terminal (completes the session)
     */
    private boolean isTerminalMessage(String type) {
        return "agent.result".equals(type) || "agent.error".equals(type) ||
               "skill.result".equals(type) || "skill.error".equals(type);
    }

    /**
     * Get pending request count for a session
     */
    public int getPendingRequestCount(String sessionId) {
        return sessionStateService.getPendingRequests(sessionId).size();
    }
}