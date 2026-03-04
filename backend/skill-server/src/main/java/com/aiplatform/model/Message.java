package com.aiplatform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Message entity representing a message in a skill session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "message_id", unique = true, length = 100)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_calls", columnDefinition = "json")
    private String toolCalls;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(length = 255)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String metadata;

    @Column(name = "tokens_input")
    @Builder.Default
    private Integer tokensInput = 0;

    @Column(name = "tokens_output")
    @Builder.Default
    private Integer tokensOutput = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

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