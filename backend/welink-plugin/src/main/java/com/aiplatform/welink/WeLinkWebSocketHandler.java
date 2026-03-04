package com.aiplatform.welink;

import com.aiplatform.config.GatewayClientProperties;
import com.aiplatform.model.WeLinkMessage;
import com.aiplatform.service.GatewayInvocationService;
import com.aiplatform.service.SkillInvocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

/**
 * WebSocket handler for WeLink messages.
 * Routes messages through gateway client (dual-identity mode) or direct HTTP fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeLinkWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final GatewayInvocationService gatewayInvocationService;
    private final SkillInvocationService skillInvocationService;
    private final GatewayClientProperties gatewayProperties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WeLink WebSocket connection established: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            log.debug("Received WeLink message: {}", payload);

            try {
                WeLinkMessage welinkMessage = objectMapper.readValue(payload, WeLinkMessage.class);

                // Process the message asynchronously
                processMessageAsync(welinkMessage);

                // Send acknowledgment
                sendAck(session, welinkMessage);
            } catch (Exception e) {
                log.error("Error processing WeLink message", e);
                sendError(session, "Failed to process message");
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WeLink WebSocket transport error for session: {}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WeLink WebSocket connection closed: {} - {}", session.getId(), status);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    /**
     * Process message asynchronously.
     * Routes through gateway if enabled, otherwise uses direct HTTP.
     */
    private void processMessageAsync(WeLinkMessage message) {
        // Check if this is a valid message that should invoke a skill
        if (!shouldInvokeSkill(message)) {
            return;
        }

        // Use dual-identity mode: gateway with fallback
        if (gatewayProperties.isEnabled()) {
            log.debug("Routing message through gateway: msgId={}", message.getMsgId());
            gatewayInvocationService.invokeSkill(message);
        } else {
            // Gateway disabled, use direct HTTP
            log.debug("Gateway disabled, using direct HTTP: msgId={}", message.getMsgId());
            skillInvocationService.invokeSkill(message);
        }
    }

    /**
     * Check if the message should invoke a skill
     */
    private boolean shouldInvokeSkill(WeLinkMessage message) {
        // Filter out system messages, empty messages, etc.
        if (message.getContent() == null || message.getContent().isBlank()) {
            return false;
        }

        // Could add more filtering logic here (e.g., command prefix, bot mentions)
        return true;
    }

    /**
     * Send acknowledgment to WeLink
     */
    private void sendAck(WebSocketSession session, WeLinkMessage message) {
        try {
            String ack = objectMapper.writeValueAsString(Map.of(
                    "type", "ack",
                    "msg_id", message.getMsgId(),
                    "timestamp", System.currentTimeMillis()
            ));
            session.sendMessage(new TextMessage(ack));
        } catch (Exception e) {
            log.error("Error sending ack", e);
        }
    }

    /**
     * Send error message
     */
    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            String error = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            ));
            session.sendMessage(new TextMessage(error));
        } catch (Exception e) {
            log.error("Error sending error message", e);
        }
    }

    // Helper for Map.of
    private static <K, V> java.util.Map<K, V> Map(K k1, V v1, K k2, V v2, K k3, V v3) {
        java.util.Map<K, V> map = new java.util.HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        return map;
    }

    private static <K, V> java.util.Map<K, V> Map(K k1, V v1, K k2, V v2) {
        java.util.Map<K, V> map = new java.util.HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}