package com.aiplatform.agent;

import com.aiplatform.dto.WebSocketMessage;
import reactor.core.publisher.Flux;

/**
 * Agent adapter interface for protocol adaptation.
 * Supports different agent protocols and message formats.
 */
public interface AgentAdapter {

    /**
     * Get the agent type this adapter handles
     * @return agent type identifier
     */
    String getAgentType();

    /**
     * Execute an agent request and return a stream of responses
     * @param context execution context containing request and session info
     * @return flux of WebSocket messages (progress, result, error)
     */
    Flux<WebSocketMessage.WebSocketMessageBase> execute(AgentExecutionContext context);

    /**
     * Check if this adapter supports the given protocol type
     * @param protocolType protocol type to check
     * @return true if supported
     */
    boolean supports(String protocolType);

    /**
     * Check if this adapter supports streaming responses
     * @return true if streaming is supported
     */
    default boolean supportsStreaming() {
        return true;
    }

    /**
     * Cancel an ongoing execution
     * @param sessionId session to cancel
     */
    default void cancel(String sessionId) {
        // Default: no-op
    }
}