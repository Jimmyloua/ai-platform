package com.aiplatform.routing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Represents an endpoint in a session.
 * An endpoint can be an agent side (plugin + agent) or client side (CUI/IM client).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEndpoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Type of endpoint
     */
    public enum Type {
        AGENT,      // Agent side (connected via plugin)
        CLIENT      // Client side (CUI/IM)
    }

    /**
     * Endpoint type
     */
    private Type type;

    /**
     * Gateway instance ID that owns this endpoint
     */
    private String gatewayId;

    /**
     * Connection ID within the gateway
     */
    private String connectionId;

    /**
     * For AGENT type: plugin ID
     */
    private String pluginId;

    /**
     * For AGENT type: agent instance ID
     */
    private String agentId;

    /**
     * For CLIENT type: client ID
     */
    private String clientId;

    /**
     * Protocol type (opencode, skill, etc.)
     */
    private String protocolType;

    /**
     * Timestamp when endpoint was registered
     */
    private long registeredAt;

    /**
     * Create agent endpoint
     */
    public static SessionEndpoint forAgent(String gatewayId, String connectionId,
                                            String pluginId, String agentId, String protocolType) {
        return SessionEndpoint.builder()
                .type(Type.AGENT)
                .gatewayId(gatewayId)
                .connectionId(connectionId)
                .pluginId(pluginId)
                .agentId(agentId)
                .protocolType(protocolType)
                .registeredAt(System.currentTimeMillis())
                .build();
    }

    /**
     * Create client endpoint
     */
    public static SessionEndpoint forClient(String gatewayId, String connectionId,
                                             String clientId, String protocolType) {
        return SessionEndpoint.builder()
                .type(Type.CLIENT)
                .gatewayId(gatewayId)
                .connectionId(connectionId)
                .clientId(clientId)
                .protocolType(protocolType)
                .registeredAt(System.currentTimeMillis())
                .build();
    }

    /**
     * Get the channel to send messages to this endpoint
     */
    public String getChannel() {
        if (type == Type.AGENT) {
            // Route through gateway, then to plugin
            return "gateway:" + gatewayId;
        } else {
            // Route directly to gateway
            return "gateway:" + gatewayId;
        }
    }

    /**
     * Get plugin channel (for agent endpoints)
     */
    public String getPluginChannel() {
        if (type == Type.AGENT && pluginId != null) {
            return "plugin:" + pluginId;
        }
        return null;
    }
}