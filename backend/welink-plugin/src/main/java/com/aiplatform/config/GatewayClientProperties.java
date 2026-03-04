package com.aiplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Gateway client configuration properties.
 * Configures connection to the skill-gateway with AK/SK authentication.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "gateway.client")
public class GatewayClientProperties {

    /**
     * Enable dual-mode (gateway + HTTP fallback)
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
     * Enable fallback to direct HTTP when gateway unavailable
     */
    private boolean fallbackEnabled = true;

    /**
     * Connection timeout in milliseconds
     */
    @Positive
    private long connectionTimeout = 10000;

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