package com.aiplatform.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * AK/SK Signature Validator
 *
 * Validates request signatures using HMAC-SHA256.
 * Signature format: HMAC-SHA256(method + "\n" + path + "\n" + timestamp + "\n" + accessKey + "\n" + bodyHash)
 */
@Slf4j
@Component
public class SignatureValidator {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    @Value("${app.gateway.auth.aksk.signature-version:v1}")
    private String signatureVersion;

    @Value("${app.gateway.auth.aksk.signature-ttl:300000}")
    private long signatureTtl;

    /**
     * Validate signature
     *
     * @param method HTTP method
     * @param path Request path
     * @param timestamp Request timestamp (milliseconds since epoch)
     * @param accessKey Access key
     * @param body Request body
     * @param signature Signature to validate
     * @param secretKey Secret key for the access key
     * @return true if signature is valid
     */
    public boolean validate(HttpMethod method, String path, long timestamp,
                           String accessKey, String body, String signature, String secretKey) {
        try {
            // Check timestamp is within acceptable range
            if (!isTimestampValid(timestamp)) {
                log.warn("Signature timestamp expired: {}", timestamp);
                return false;
            }

            // Calculate expected signature
            String expectedSignature = calculateSignature(method, path, timestamp, accessKey, body, secretKey);

            // Compare signatures (constant-time comparison)
            return constantTimeEquals(expectedSignature, signature);
        } catch (Exception e) {
            log.error("Error validating signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Calculate signature
     */
    public String calculateSignature(HttpMethod method, String path, long timestamp,
                                     String accessKey, String body, String secretKey) {
        try {
            // Calculate body hash
            String bodyHash = calculateBodyHash(body);

            // Build string to sign
            String stringToSign = buildStringToSign(method, path, timestamp, accessKey, bodyHash);

            // Sign with HMAC-SHA256
            return hmacSha256(stringToSign, secretKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate signature", e);
        }
    }

    /**
     * Build string to sign
     */
    private String buildStringToSign(HttpMethod method, String path, long timestamp,
                                     String accessKey, String bodyHash) {
        return new StringBuilder()
                .append(method.name()).append("\n")
                .append(path).append("\n")
                .append(timestamp).append("\n")
                .append(accessKey).append("\n")
                .append(bodyHash)
                .toString();
    }

    /**
     * Calculate SHA-256 hash of body
     */
    private String calculateBodyHash(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest((body != null ? body : "").getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * HMAC-SHA256
     */
    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    /**
     * Check if timestamp is within acceptable range
     */
    private boolean isTimestampValid(long timestamp) {
        Instant requestTime = Instant.ofEpochMilli(timestamp);
        Instant now = Instant.now();

        long diffMs = Math.abs(ChronoUnit.MILLIS.between(requestTime, now));
        return diffMs <= signatureTtl;
    }

    /**
     * Constant-time string comparison to prevent timing attacks
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }

        return result == 0;
    }
}