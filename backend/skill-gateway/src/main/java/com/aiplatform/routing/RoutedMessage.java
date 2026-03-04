package com.aiplatform.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Routed message for inter-gateway communication.
 * Contains the message payload and routing information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutedMessage {

    /**
     * Message direction
     */
    public enum Direction {
        AGENT_TO_CLIENT,    // Agent sending to client
        CLIENT_TO_AGENT     // Client sending to agent
    }

    /**
     * Session ID for routing
     */
    private String sessionId;

    /**
     * Message direction
     */
    private Direction direction;

    /**
     * Source endpoint info
     */
    private String sourceGatewayId;
    private String sourceConnectionId;

    /**
     * Target endpoint info (resolved by gateway router)
     */
    private String targetGatewayId;
    private String targetConnectionId;
    private String targetPluginId;  // For agent endpoints

    /**
     * Protocol type of the message
     */
    private String protocolType;

    /**
     * Message type (skill.execute, agent.execute, etc.)
     */
    private String messageType;

    /**
     * Message payload (JSON string)
     */
    private String payload;

    /**
     * Additional metadata
     */
    private Map<String, String> metadata;

    /**
     * Timestamp
     */
    private long timestamp;

    /**
     * Create a routed message from client to agent
     */
    public static RoutedMessage fromClient(String sessionId, String sourceGatewayId,
                                            String sourceConnectionId, String protocolType,
                                            String messageType, String payload) {
        return RoutedMessage.builder()
                .sessionId(sessionId)
                .direction(Direction.CLIENT_TO_AGENT)
                .sourceGatewayId(sourceGatewayId)
                .sourceConnectionId(sourceConnectionId)
                .protocolType(protocolType)
                .messageType(messageType)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Create a routed message from agent to client
     */
    public static RoutedMessage fromAgent(String sessionId, String sourceGatewayId,
                                           String sourceConnectionId, String pluginId,
                                           String protocolType, String messageType, String payload) {
        return RoutedMessage.builder()
                .sessionId(sessionId)
                .direction(Direction.AGENT_TO_CLIENT)
                .sourceGatewayId(sourceGatewayId)
                .sourceConnectionId(sourceConnectionId)
                .targetPluginId(pluginId)
                .protocolType(protocolType)
                .messageType(messageType)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Get Redis channel for this message
     */
    public String getChannel() {
        if (targetGatewayId != null) {
            return "gateway:" + targetGatewayId;
        }
        return null;
    }

    /**
     * Get plugin channel if targeting a plugin
     */
    public String getPluginChannel() {
        if (targetPluginId != null) {
            return "plugin:" + targetPluginId;
        }
        return null;
    }
}