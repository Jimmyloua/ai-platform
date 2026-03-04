package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WeLink access token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeLinkToken {

    /**
     * Access token
     */
    @JsonProperty("access_token")
    private String accessToken;

    /**
     * Token expiration time in seconds
     */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /**
     * Token type
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * Token creation timestamp
     */
    private Long createdAt;

    /**
     * Check if token is expired
     */
    public boolean isExpired() {
        if (createdAt == null || expiresIn == null) {
            return true;
        }
        // Add 60 seconds buffer
        return System.currentTimeMillis() > (createdAt + (expiresIn - 60) * 1000);
    }
}