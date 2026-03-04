package com.aiplatform.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * WeLink API response wrapper
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeLinkApiResponse<T> {

    /**
     * Response code (0 = success)
     */
    @JsonProperty("code")
    private Integer code;

    /**
     * Response message
     */
    @JsonProperty("message")
    private String message;

    /**
     * Response data
     */
    @JsonProperty("data")
    private T data;

    /**
     * Request ID for tracing
     */
    @JsonProperty("request_id")
    private String requestId;

    /**
     * Check if response is successful
     */
    public boolean isSuccess() {
        return code != null && code == 0;
    }
}