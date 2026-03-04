package com.aiplatform.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AK/SK Authentication Filter for Gateway
 *
 * Handles authentication for WebSocket connections and API requests
 * using Access Key / Secret Key signature validation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AkSkAuthFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String ACCESS_KEY_HEADER = "X-Access-Key";
    private static final String AUTH_PREFIX = "SIGN ";

    private final SignatureValidator signatureValidator;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.gateway.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.gateway.rate-limit.requests-per-second:100}")
    private int requestsPerSecond;

    /**
     * Authenticate request
     */
    public Mono<Authentication> authenticate(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // Extract headers
        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);
        String timestampStr = request.getHeaders().getFirst(TIMESTAMP_HEADER);
        String accessKey = request.getHeaders().getFirst(ACCESS_KEY_HEADER);

        // Validate headers present
        if (authHeader == null || timestampStr == null || accessKey == null) {
            log.debug("Missing authentication headers");
            return Mono.empty();
        }

        // Parse timestamp
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.debug("Invalid timestamp format");
            return Mono.empty();
        }

        // Extract signature
        if (!authHeader.startsWith(AUTH_PREFIX)) {
            log.debug("Invalid authorization header format");
            return Mono.empty();
        }
        String signature = authHeader.substring(AUTH_PREFIX.length());

        // Get secret key for access key (from cache or database)
        return getSecretKey(accessKey)
                .flatMap(secretKey -> {
                    // Validate signature
                    HttpMethod method = request.getMethod();
                    String path = request.getPath().value();

                    // Get body for POST/PUT requests
                    return extractBody(request)
                            .defaultIfEmpty("")
                            .flatMap(body -> {
                                boolean valid = signatureValidator.validate(
                                        method, path, timestamp, accessKey, body, signature, secretKey
                                );

                                if (!valid) {
                                    log.warn("Invalid signature for access key: {}", accessKey);
                                    return Mono.empty();
                                }

                                // Check rate limit
                                if (rateLimitEnabled && !checkRateLimit(accessKey)) {
                                    log.warn("Rate limit exceeded for access key: {}", accessKey);
                                    return Mono.empty();
                                }

                                // Create authentication
                                Authentication auth = new UsernamePasswordAuthenticationToken(
                                        accessKey,
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_SKILL"))
                                );

                                return Mono.just(auth);
                            });
                });
    }

    /**
     * Get secret key for access key
     */
    private Mono<String> getSecretKey(String accessKey) {
        // Try cache first
        String cacheKey = "aksk:" + accessKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return Mono.just(cached);
        }

        // In real implementation, fetch from database or credential service
        // For now, return empty to indicate not found
        log.debug("Secret key not found for access key: {}", accessKey);
        return Mono.empty();
    }

    /**
     * Extract request body
     */
    private Mono<String> extractBody(ServerHttpRequest request) {
        // For WebSocket upgrade, no body
        if (isWebSocketUpgrade(request)) {
            return Mono.just("");
        }

        // For other requests, body would be extracted from request
        // In reactive context, this requires caching the request body
        return Mono.just("");
    }

    /**
     * Check if this is a WebSocket upgrade request
     */
    private boolean isWebSocketUpgrade(ServerHttpRequest request) {
        String upgrade = request.getHeaders().getFirst("Upgrade");
        return "websocket".equalsIgnoreCase(upgrade);
    }

    /**
     * Check rate limit for access key
     */
    private boolean checkRateLimit(String accessKey) {
        String rateLimitKey = "ratelimit:" + accessKey;
        Long current = redisTemplate.opsForValue().increment(rateLimitKey);

        if (current == 1) {
            // First request, set expiry
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.SECONDS);
        }

        return current != null && current <= requestsPerSecond;
    }

    /**
     * Cache secret key
     */
    public void cacheSecretKey(String accessKey, String secretKey) {
        String cacheKey = "aksk:" + accessKey;
        redisTemplate.opsForValue().set(cacheKey, secretKey, Duration.ofMinutes(30));
    }

    /**
     * Remove cached secret key
     */
    public void removeCachedSecretKey(String accessKey) {
        String cacheKey = "aksk:" + accessKey;
        redisTemplate.delete(cacheKey);
    }
}