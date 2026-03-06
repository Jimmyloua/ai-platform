package com.aiplatform.agent;

import com.aiplatform.dto.OpenCodeMessage;
import com.aiplatform.dto.WebSocketMessage;
import com.aiplatform.gateway.GatewayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Gateway routing adapter that forwards requests to SkillGateway.
 * This adapter routes messages through the gateway to agents (like WeLinkPlugin).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayRoutingAdapter implements AgentAdapter {

    private final GatewayClient gatewayClient;

    @Override
    public String getAgentType() {
        return "GATEWAY_ROUTER";
    }

    @Override
    public Flux<WebSocketMessage.WebSocketMessageBase> execute(AgentExecutionContext context) {
        log.info("Routing request through gateway: sessionId={}, protocolType={}",
                context.getSessionId(), context.getProtocolType());

        // Check if gateway is connected
        if (!gatewayClient.isConnected()) {
            log.warn("Gateway not connected, cannot route request");
            return Flux.error(new RuntimeException("Gateway not connected"));
        }

        // Build the message to route
        WebSocketMessage.WebSocketMessageBase message = buildMessage(context);

        // Route through gateway
        return gatewayClient.routeToGateway(context.getSessionId(), message);
    }

    @Override
    public boolean supports(String protocolType) {
        // This adapter supports all protocols when gateway is available
        return true;
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public void cancel(String sessionId) {
        log.info("Cancelling session via gateway: {}", sessionId);
        gatewayClient.unregisterSession(sessionId);
    }

    /**
     * Build the appropriate message based on context
     */
    private WebSocketMessage.WebSocketMessageBase buildMessage(AgentExecutionContext context) {
        String protocolType = context.getProtocolType();

        // Agent execute protocol
        if (OpenCodeMessage.AgentExecuteRequest.TYPE.equals(protocolType) ||
            (protocolType != null && protocolType.startsWith("agent."))) {
            return buildAgentExecuteMessage(context);
        }

        // Skill execute protocol (legacy)
        return buildSkillExecuteMessage(context);
    }

    /**
     * Build agent execute message
     */
    private WebSocketMessage.WebSocketMessageBase buildAgentExecuteMessage(AgentExecutionContext context) {
        OpenCodeMessage.AgentExecuteRequest.AgentExecuteRequestBuilder builder =
                OpenCodeMessage.AgentExecuteRequest.builder()
                        .sessionId(context.getSessionId());

        if (context.getAgentRequest() != null) {
            builder.payload(context.getAgentRequest().getPayload());
        } else {
            // Build from skill request
            OpenCodeMessage.AgentExecuteRequest.Payload.PayloadBuilder payloadBuilder =
                    OpenCodeMessage.AgentExecuteRequest.Payload.builder()
                            .stream(context.isStreaming())
                            .skillId(context.getSkillId())
                            .skillName(context.getSkillName())
                            .channel(context.getChannel());

            if (context.getSkillRequest() != null && context.getSkillRequest().getPayload() != null) {
                var skillPayload = context.getSkillRequest().getPayload();
                payloadBuilder.options(skillPayload.getOptions());
            }

            builder.payload(payloadBuilder.build());
        }

        return builder.build();
    }

    /**
     * Build skill execute message (legacy protocol)
     */
    private WebSocketMessage.WebSocketMessageBase buildSkillExecuteMessage(AgentExecutionContext context) {
        WebSocketMessage.SkillExecuteRequest.SkillExecuteRequestBuilder builder =
                WebSocketMessage.SkillExecuteRequest.builder()
                        .sessionId(context.getSessionId());

        if (context.getSkillRequest() != null) {
            builder.payload(context.getSkillRequest().getPayload());
        } else {
            // Build minimal payload
            WebSocketMessage.SkillExecuteRequest.Payload payload =
                    WebSocketMessage.SkillExecuteRequest.Payload.builder()
                            .skillId(context.getSkillId())
                            .skillName(context.getSkillName())
                            .channel(context.getChannel())
                            .build();
            builder.payload(payload);
        }

        return builder.build();
    }
}