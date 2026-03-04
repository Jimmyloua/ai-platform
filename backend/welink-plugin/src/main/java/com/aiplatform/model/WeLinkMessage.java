package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * WeLink message model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeLinkMessage {

    /**
     * Message ID
     */
    @JsonProperty("msg_id")
    private String msgId;

    /**
     * Message type (text, image, file, etc.)
     */
    @JsonProperty("msg_type")
    private String msgType;

    /**
     * Sender user ID
     */
    @JsonProperty("from_user_id")
    private String fromUserId;

    /**
     * Sender name
     */
    @JsonProperty("from_user_name")
    private String fromUserName;

    /**
     * Receiver user ID (for direct messages)
     */
    @JsonProperty("to_user_id")
    private String toUserId;

    /**
     * Group ID (for group messages)
     */
    @JsonProperty("group_id")
    private String groupId;

    /**
     * Chat type: single, group
     */
    @JsonProperty("chat_type")
    private String chatType;

    /**
     * Message content
     */
    @JsonProperty("content")
    private String content;

    /**
     * Message timestamp
     */
    @JsonProperty("timestamp")
    private Long timestamp;

    /**
     * Tenant ID
     */
    @JsonProperty("tenant_id")
    private String tenantId;

    /**
     * Correlation ID for tracking
     */
    @JsonProperty("correlation_id")
    private String correlationId;

    /**
     * Additional metadata
     */
    @JsonProperty("metadata")
    private Object metadata;

    /**
     * Get timestamp as Instant
     */
    public Instant getTimestampAsInstant() {
        return timestamp != null ? Instant.ofEpochMilli(timestamp) : null;
    }
}