package com.aiplatform.service;

import com.aiplatform.config.WeLinkProperties;
import com.aiplatform.model.WeLinkApiResponse;
import com.aiplatform.model.WeLinkToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service for WeLink API authentication
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeLinkAuthService {

    private static final String TOKEN_CACHE_KEY = "welink:token";

    private final WeLinkProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        webClient = WebClient.builder()
                .baseUrl(properties.getApi().getBaseUrl())
                .build();
    }

    /**
     * Get valid access token
     */
    public String getAccessToken() {
        // Try to get from cache
        WeLinkToken cachedToken = getCachedToken();
        if (cachedToken != null && !cachedToken.isExpired()) {
            return cachedToken.getAccessToken();
        }

        // Refresh token
        WeLinkToken newToken = fetchNewToken();
        if (newToken != null) {
            cacheToken(newToken);
            return newToken.getAccessToken();
        }

        throw new RuntimeException("Failed to obtain WeLink access token");
    }

    /**
     * Fetch new access token from WeLink API
     */
    @SuppressWarnings("unchecked")
    private WeLinkToken fetchNewToken() {
        try {
            String url = "/auth/v1/tokens";

            Map<String, String> requestBody = Map.of(
                    "client_id", properties.getApi().getAppId(),
                    "client_secret", properties.getApi().getAppSecret()
            );

            WeLinkApiResponse<Map<String, Object>> response = webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(this::parseTokenResponse)
                    .block(Duration.ofMillis(properties.getApi().getTimeout()));

            if (response != null && response.isSuccess() && response.getData() != null) {
                return WeLinkToken.builder()
                        .accessToken((String) response.getData().get("access_token"))
                        .expiresIn(((Number) response.getData().get("expires_in")).longValue())
                        .tokenType((String) response.getData().get("token_type"))
                        .createdAt(System.currentTimeMillis())
                        .build();
            }

            log.error("Failed to fetch token: {}", response != null ? response.getMessage() : "null response");
            return null;
        } catch (Exception e) {
            log.error("Error fetching WeLink access token", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private WeLinkApiResponse<Map<String, Object>> parseTokenResponse(Map<String, Object> response) {
        return WeLinkApiResponse.<Map<String, Object>>builder()
                .code((Integer) response.get("code"))
                .message((String) response.get("message"))
                .data((Map<String, Object>) response.get("data"))
                .requestId((String) response.get("request_id"))
                .build();
    }

    /**
     * Cache token in Redis
     */
    private void cacheToken(WeLinkToken token) {
        redisTemplate.opsForValue().set(
                TOKEN_CACHE_KEY,
                token,
                token.getExpiresIn() - 60,
                TimeUnit.SECONDS
        );
    }

    /**
     * Get cached token from Redis
     */
    private WeLinkToken getCachedToken() {
        try {
            Object cached = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
            if (cached instanceof WeLinkToken) {
                return (WeLinkToken) cached;
            }
            if (cached instanceof Map) {
                return objectMapper.convertValue(cached, WeLinkToken.class);
            }
            return null;
        } catch (Exception e) {
            log.warn("Error getting cached token", e);
            return null;
        }
    }

    /**
     * Invalidate cached token
     */
    public void invalidateToken() {
        redisTemplate.delete(TOKEN_CACHE_KEY);
    }
}