package com.aiplatform.service;

import com.aiplatform.config.WeLinkProperties;
import com.aiplatform.model.WeLinkApiResponse;
import com.aiplatform.model.WeLinkMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for sending messages to WeLink
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeLinkMessageService {

    private final WeLinkProperties properties;
    private final WeLinkAuthService authService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .build();
    }

    /**
     * Send a text message to a user
     */
    public boolean sendTextMessage(String toUserId, String content) {
        return sendTextMessage(toUserId, null, content);
    }

    /**
     * Send a text message (direct or group)
     */
    public boolean sendTextMessage(String toUserId, String groupId, String content) {
        try {
            String accessToken = authService.getAccessToken();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msg_type", "text");
            requestBody.put("content", content);

            if (groupId != null) {
                requestBody.put("group_id", groupId);
                requestBody.put("chat_type", "group");
            } else {
                requestBody.put("to_user_id", toUserId);
                requestBody.put("chat_type", "single");
            }

            WeLinkApiResponse<Void> response = webClient.post()
                    .uri("/api/v1/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(this::parseResponse)
                    .block(Duration.ofMillis(properties.getApi().getTimeout()));

            return response != null && response.isSuccess();
        } catch (Exception e) {
            log.error("Error sending message to WeLink", e);
            return false;
        }
    }

    /**
     * Send markdown message
     */
    public boolean sendMarkdownMessage(String toUserId, String content) {
        return sendMarkdownMessage(toUserId, null, content);
    }

    /**
     * Send markdown message (direct or group)
     */
    public boolean sendMarkdownMessage(String toUserId, String groupId, String content) {
        try {
            String accessToken = authService.getAccessToken();

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msg_type", "markdown");
            requestBody.put("content", content);

            if (groupId != null) {
                requestBody.put("group_id", groupId);
                requestBody.put("chat_type", "group");
            } else {
                requestBody.put("to_user_id", toUserId);
                requestBody.put("chat_type", "single");
            }

            WeLinkApiResponse<Void> response = webClient.post()
                    .uri("/api/v1/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(this::parseResponse)
                    .block(Duration.ofMillis(properties.getApi().getTimeout()));

            return response != null && response.isSuccess();
        } catch (Exception e) {
            log.error("Error sending markdown message to WeLink", e);
            return false;
        }
    }

    /**
     * Queue message for async processing
     */
    public void queueMessage(WeLinkMessage message) {
        String queueKey = properties.getMessage().getQueuePrefix() + "pending";
        redisTemplate.opsForList().rightPush(queueKey, message);
    }

    /**
     * Get next queued message
     */
    public WeLinkMessage getNextQueuedMessage() {
        String queueKey = properties.getMessage().getQueuePrefix() + "pending";
        Object message = redisTemplate.opsForList().leftPop(queueKey, 5, TimeUnit.SECONDS);
        if (message instanceof WeLinkMessage) {
            return (WeLinkMessage) message;
        }
        if (message instanceof Map) {
            return objectMapper.convertValue(message, WeLinkMessage.class);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private WeLinkApiResponse<Void> parseResponse(Map<String, Object> response) {
        return WeLinkApiResponse.<Void>builder()
                .code((Integer) response.get("code"))
                .message((String) response.get("message"))
                .requestId((String) response.get("request_id"))
                .build();
    }
}