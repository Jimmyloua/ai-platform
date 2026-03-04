package com.aiplatform.service;

import com.aiplatform.config.WeLinkProperties;
import com.aiplatform.model.WeLinkMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for invoking skills on the Skill Server
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillInvocationService {

    private final WeLinkProperties properties;
    private final WeLinkMessageService messageService;
    private final ObjectMapper objectMapper;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
                .baseUrl(properties.getSkillServer().getUrl())
                .build();
    }

    /**
     * Invoke a skill from a WeLink message
     */
    public void invokeSkill(WeLinkMessage welinkMessage) {
        try {
            String sessionId = UUID.randomUUID().toString();

            // Build skill execution request
            Map<String, Object> request = new HashMap<>();
            request.put("sessionId", sessionId);
            request.put("channel", "im");
            request.put("userId", welinkMessage.getFromUserId());
            request.put("input", Map.of(
                    "message", welinkMessage.getContent(),
                    "chatType", welinkMessage.getChatType(),
                    "groupId", welinkMessage.getGroupId() != null ? welinkMessage.getGroupId() : "",
                    "msgId", welinkMessage.getMsgId()
            ));
            request.put("metadata", Map.of(
                    "platform", "welink",
                    "tenantId", welinkMessage.getTenantId(),
                    "correlationId", welinkMessage.getCorrelationId() != null
                            ? welinkMessage.getCorrelationId()
                            : sessionId
            ));

            // Send to skill server
            webClient.post()
                    .uri("/api/v1/skills/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(properties.getSkillServer().getTimeout()))
                    .subscribe(
                            response -> handleSkillResponse(welinkMessage, response),
                            error -> handleSkillError(welinkMessage, error)
                    );

            log.info("Skill invocation started for message: {}", welinkMessage.getMsgId());
        } catch (Exception e) {
            log.error("Error invoking skill for message: {}", welinkMessage.getMsgId(), e);
            sendErrorMessage(welinkMessage, "Failed to process your request. Please try again.");
        }
    }

    /**
     * Handle skill execution response
     */
    @SuppressWarnings("unchecked")
    private void handleSkillResponse(WeLinkMessage welinkMessage, Map<String, Object> response) {
        try {
            String status = (String) response.get("status");
            Object result = response.get("result");

            if ("completed".equals(status) && result != null) {
                String content;
                if (result instanceof String) {
                    content = (String) result;
                } else {
                    content = objectMapper.writeValueAsString(result);
                }

                // Send response back to WeLink
                messageService.sendTextMessage(
                        welinkMessage.getFromUserId(),
                        welinkMessage.getGroupId(),
                        content
                );
            } else if ("failed".equals(status)) {
                String errorMessage = (String) response.get("errorMessage");
                sendErrorMessage(welinkMessage, errorMessage != null ? errorMessage : "Skill execution failed");
            }
        } catch (Exception e) {
            log.error("Error handling skill response", e);
        }
    }

    /**
     * Handle skill execution error
     */
    private void handleSkillError(WeLinkMessage welinkMessage, Throwable error) {
        log.error("Skill execution error for message: {}", welinkMessage.getMsgId(), error);
        sendErrorMessage(welinkMessage, "An error occurred while processing your request.");
    }

    /**
     * Send error message to user
     */
    private void sendErrorMessage(WeLinkMessage welinkMessage, String errorText) {
        messageService.sendTextMessage(
                welinkMessage.getFromUserId(),
                welinkMessage.getGroupId(),
                "❌ " + errorText
        );
    }
}