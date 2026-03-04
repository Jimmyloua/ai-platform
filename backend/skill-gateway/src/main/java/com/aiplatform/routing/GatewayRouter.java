package com.aiplatform.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gateway Router for routing messages between gateways.
 * Resolves target endpoints and routes messages through Redis pub/sub.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayRouter {

    private final SessionRegistry sessionRegistry;
    private final RoutedMessagePublisher messagePublisher;

    @Value("${app.gateway.id:}")
    private String configuredGatewayId;

    /**
     * Get this gateway's unique ID
     */
    public String getGatewayId() {
        if (configuredGatewayId != null && !configuredGatewayId.isEmpty()) {
            return configuredGatewayId;
        }
        // Generate a unique ID if not configured
        return System.getenv().getOrDefault("HOSTNAME", "gateway-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Route a message from client to agent
     */
    public boolean routeToAgent(String sessionId, String sourceConnectionId,
                                 String protocolType, String messageType, String payload) {
        try {
            // Get agent endpoint for this session
            var agentEndpointOpt = sessionRegistry.getAgentEndpoint(sessionId);

            if (agentEndpointOpt.isEmpty()) {
                log.warn("No agent endpoint found for session: {}", sessionId);
                return false;
            }

            SessionEndpoint agentEndpoint = agentEndpointOpt.get();

            // Create routed message
            RoutedMessage message = RoutedMessage.fromClient(
                    sessionId,
                    getGatewayId(),
                    sourceConnectionId,
                    protocolType,
                    messageType,
                    payload
            );

            // Set target
            message.setTargetGatewayId(agentEndpoint.getGatewayId());
            message.setTargetConnectionId(agentEndpoint.getConnectionId());
            message.setTargetPluginId(agentEndpoint.getPluginId());

            // Route to agent's gateway
            if (agentEndpoint.getGatewayId().equals(getGatewayId())) {
                // Same gateway - deliver locally
                log.debug("Routing message locally to agent: sessionId={}", sessionId);
                return true; // Will be handled by local connection
            } else {
                // Different gateway - publish to Redis
                log.debug("Routing message to remote gateway: sessionId={}, targetGateway={}",
                        sessionId, agentEndpoint.getGatewayId());
                return messagePublisher.publishToGateway(agentEndpoint.getGatewayId(), message);
            }

        } catch (Exception e) {
            log.error("Failed to route message to agent: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Route a message from agent to client
     */
    public boolean routeToClient(String sessionId, String sourceConnectionId, String pluginId,
                                  String protocolType, String messageType, String payload) {
        try {
            // Get client endpoint for this session
            var clientEndpointOpt = sessionRegistry.getClientEndpoint(sessionId);

            if (clientEndpointOpt.isEmpty()) {
                log.warn("No client endpoint found for session: {}", sessionId);
                return false;
            }

            SessionEndpoint clientEndpoint = clientEndpointOpt.get();

            // Create routed message
            RoutedMessage message = RoutedMessage.fromAgent(
                    sessionId,
                    getGatewayId(),
                    sourceConnectionId,
                    pluginId,
                    protocolType,
                    messageType,
                    payload
            );

            // Set target
            message.setTargetGatewayId(clientEndpoint.getGatewayId());
            message.setTargetConnectionId(clientEndpoint.getConnectionId());

            // Route to client's gateway
            if (clientEndpoint.getGatewayId().equals(getGatewayId())) {
                // Same gateway - deliver locally
                log.debug("Routing message locally to client: sessionId={}", sessionId);
                return true; // Will be handled by local connection
            } else {
                // Different gateway - publish to Redis
                log.debug("Routing message to remote gateway: sessionId={}, targetGateway={}",
                        sessionId, clientEndpoint.getGatewayId());
                return messagePublisher.publishToGateway(clientEndpoint.getGatewayId(), message);
            }

        } catch (Exception e) {
            log.error("Failed to route message to client: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if a session's peer is on this gateway
     */
    public boolean isPeerLocal(String sessionId, SessionEndpoint.Type fromType) {
        var peerEndpointOpt = sessionRegistry.getPeerEndpoint(sessionId, fromType);
        if (peerEndpointOpt.isEmpty()) {
            return false;
        }
        return peerEndpointOpt.get().getGatewayId().equals(getGatewayId());
    }

    /**
     * Get the target connection ID for a session
     */
    public String getTargetConnectionId(String sessionId, SessionEndpoint.Type fromType) {
        var peerEndpointOpt = sessionRegistry.getPeerEndpoint(sessionId, fromType);
        return peerEndpointOpt.map(SessionEndpoint::getConnectionId).orElse(null);
    }
}