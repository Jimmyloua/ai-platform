package com.aiplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * WeLink configuration properties
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "welink")
public class WeLinkProperties {

    /**
     * WeLink API configuration
     */
    @NotNull
    private ApiConfig api = new ApiConfig();

    /**
     * WebSocket configuration
     */
    @NotNull
    private WebSocketConfig websocket = new WebSocketConfig();

    /**
     * Message configuration
     */
    @NotNull
    private MessageConfig message = new MessageConfig();

    /**
     * Skill Server configuration
     */
    @NotNull
    private SkillServerConfig skillServer = new SkillServerConfig();

    @Data
    public static class ApiConfig {
        /**
         * WeLink Open API base URL
         */
        @NotBlank
        private String baseUrl = "https://open.welink.huaweicloud.com";

        /**
         * WeLink App ID
         */
        private String appId;

        /**
         * WeLink App Secret
         */
        private String appSecret;

        /**
         * API request timeout in milliseconds
         */
        @Positive
        private int timeout = 30000;
    }

    @Data
    public static class WebSocketConfig {
        /**
         * WeLink WebSocket URL
         */
        @NotBlank
        private String url = "wss://open.welink.huaweicloud.com/api/wss";

        /**
         * Reconnection interval in milliseconds
         */
        @Positive
        private long reconnectInterval = 5000;

        /**
         * Heartbeat interval in milliseconds
         */
        @Positive
        private long heartbeatInterval = 30000;
    }

    @Data
    public static class MessageConfig {
        /**
         * Redis queue prefix for messages
         */
        private String queuePrefix = "welink:msg:";

        /**
         * Maximum retry attempts for message delivery
         */
        @Positive
        private int maxRetries = 3;
    }

    @Data
    public static class SkillServerConfig {
        /**
         * Skill Server URL
         */
        @NotBlank
        private String url = "http://localhost:8080";

        /**
         * Request timeout in milliseconds
         */
        @Positive
        private long timeout = 60000;
    }
}