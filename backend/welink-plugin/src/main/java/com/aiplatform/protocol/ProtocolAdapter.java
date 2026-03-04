package com.aiplatform.protocol;

import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.model.WeLinkMessage;

/**
 * Protocol adapter interface for message transformation.
 * Supports transformation between WeLink messages and gateway protocol messages.
 */
public interface ProtocolAdapter {

    /**
     * Get the protocol type this adapter handles
     * @return protocol type identifier (e.g., "opencode", "openai")
     */
    String getProtocolType();

    /**
     * Adapt WeLink message to gateway protocol format
     * @param welinkMessage incoming WeLink message
     * @param stream whether streaming is requested
     * @return gateway protocol message
     */
    WebSocketMessage.WebSocketMessageBase adaptToGateway(WeLinkMessage welinkMessage, boolean stream);

    /**
     * Adapt gateway response to WeLink format
     * @param gatewayMessage gateway protocol message
     * @return WeLink compatible message
     */
    WeLinkMessage adaptFromGateway(WebSocketMessage.WebSocketMessageBase gatewayMessage);

    /**
     * Check if this adapter supports streaming
     * @return true if streaming is supported
     */
    default boolean supportsStreaming() {
        return true;
    }

    /**
     * Check if the message is a final response (no more chunks expected)
     * @param gatewayMessage gateway message
     * @return true if this is a final/complete response
     */
    default boolean isComplete(WebSocketMessage.WebSocketMessageBase gatewayMessage) {
        return true;
    }

    /**
     * Get message type for execute requests
     * @return message type string
     */
    default String getExecuteMessageType() {
        return "skill.execute";
    }
}