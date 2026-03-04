package com.aiplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Skill entity representing an AI skill that can be executed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    private Long id;
    private String name;
    private String description;
    @Builder.Default
    private String version = "1.0.0";
    private String endpointUrl;
    @Builder.Default
    private AuthType authType = AuthType.NONE;
    private String config;
    @Builder.Default
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    /**
     * Authentication type for skill execution
     */
    public enum AuthType {
        AKSK,   // Access Key / Secret Key
        JWT,    // JSON Web Token
        NONE    // No authentication
    }
}