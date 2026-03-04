package com.aiplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Skill Session entity representing an active skill execution session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillSession {

    private Long id;
    private String sessionId;
    private Long skillId;
    private String userId;
    @Builder.Default
    private Channel channel = Channel.CUI;
    @Builder.Default
    private Status status = Status.PENDING;
    private String inputData;
    private String outputData;
    private String errorMessage;
    private String metadata;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Channel types for skill execution
     */
    public enum Channel {
        CUI,              // Conversational UI (web)
        IM,               // Instant Messaging
        MEETING,          // Meeting integration
        ASSISTANT_SQUARE  // Assistant Square widget
    }

    /**
     * Session status
     */
    public enum Status {
        PENDING,    // Session created, waiting to start
        RUNNING,    // Skill is executing
        COMPLETED,  // Execution completed successfully
        FAILED,     // Execution failed
        PAUSED,     // Execution paused
        CANCELLED   // Execution cancelled
    }
}