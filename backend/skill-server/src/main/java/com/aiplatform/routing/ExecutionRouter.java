package com.aiplatform.routing;

import com.aiplatform.agent.AgentAdapter;
import com.aiplatform.agent.AgentAdapterRegistry;
import com.aiplatform.agent.AgentExecutionContext;
import com.aiplatform.agent.GatewayRoutingAdapter;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.gateway.GatewayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Routes execution requests to either local adapters or gateway.
 * Implements the call flow: CUI -> SkillServer -> SkillGateway -> Plugin -> Agent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionRouter {

    private final GatewayClient gatewayClient;
    private final AgentAdapterRegistry adapterRegistry;
    private final GatewayRoutingAdapter gatewayRoutingAdapter;

    /**
     * Execute a request, routing through gateway if available.
     */
    public Flux<WebSocketMessage.WebSocketMessageBase> execute(AgentExecutionContext context) {
        log.info("Routing execution: sessionId={}, protocolType={}",
                context.getSessionId(), context.getProtocolType());

        // Route through gateway if connected
        if (gatewayClient.isConnected()) {
            log.info("Routing through gateway: sessionId={}", context.getSessionId());
            return gatewayRoutingAdapter.execute(context);
        }

        // Fall back to local execution
        log.info("Gateway unavailable, using local execution: sessionId={}", context.getSessionId());
        AgentAdapter localAdapter = adapterRegistry.getAdapter(context.getProtocolType());
        return localAdapter.execute(context);
    }

    /**
     * Check if gateway routing is available
     */
    public boolean isGatewayAvailable() {
        return gatewayClient.isConnected();
    }

    /**
     * Cancel a session
     */
    public void cancel(String sessionId) {
        // Cancel on both gateway and local
        gatewayRoutingAdapter.cancel(sessionId);
    }
}