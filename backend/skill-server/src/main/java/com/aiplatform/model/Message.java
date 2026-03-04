package com.aiplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message entity representing a message in a skill session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    private Long id;
    private String sessionId;
    private String messageId;
    private Role role;
    private String content;
    private String toolCalls;
    private String toolCallId;
    private String name;
    private String metadata;
    @Builder.Default
    private Integer tokensInput = 0;
    @Builder.Default
    private Integer tokensOutput = 0;
    private LocalDateTime createdAt;

    /**
     * Message role types (OpenAI-compatible)
     */
    public enum Role {
        USER,       // User message
        ASSISTANT,  // AI assistant response
        SYSTEM,     // System instruction
        TOOL        // Tool/function response
    }
}