package com.aiplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Credential entity for AK/SK authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Credential {

    private Long id;
    private String accessKey;
    private String secretKey;
    private String userId;
    private Long skillId;
    private String permissions;
    @Builder.Default
    private Integer rateLimit = 100;
    @Builder.Default
    private Boolean isActive = true;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;

    /**
     * Check if the credential is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if the credential is valid (active and not expired)
     */
    public boolean isValid() {
        return isActive && !isExpired();
    }
}