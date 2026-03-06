package com.aiplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Gateway client configuration properties for skill-server.
 * Configures connection to the skill-gateway.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "gateway.client")
public class GatewayClientProperties {

    /**
     * Enable gateway routing (when disabled, uses local execution)
     */
    private boolean enabled = true;

    /**
     * Gateway WebSocket URL
     */
    private String wsUrl = "ws://localhost:8081/ws/gateway";

    /**
     * Access key for AK/SK authentication
     */
    private String accessKey;

    /**
     * Secret key for AK/SK authentication
     */
    private String secretKey;

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

    /**
     * Connection timeout in milliseconds
     */
    @Positive
    private long connectionTimeout = 10000;

    /**
     * Request timeout in milliseconds
     */
    @Positive
    private long requestTimeout = 60000;

    /**
     * Enable local fallback when gateway unavailable
     */
    private boolean localFallbackEnabled = true;

    /**
     * Streaming configuration
     */
    private StreamingConfig streaming = new StreamingConfig();

    @Data
    public static class StreamingConfig {
        /**
         * Enable streaming responses
         */
        private boolean enabled = true;

        /**
         * Chunk timeout in milliseconds
         */
        @Positive
        private long chunkTimeout = 30000;

        /**
         * Maximum chunks per session
         */
        private int maxChunksPerSession = 10000;
    }
}