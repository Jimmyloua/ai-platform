package com.aiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Skill Gateway Application
 *
 * Main entry point for the gateway that handles:
 * - AK/SK authentication
 * - WebSocket connection management
 * - Rate limiting and circuit breaker
 * - Multi-instance message routing via Redis pub/sub
 */
@SpringBootApplication
public class SkillGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillGatewayApplication.class, args);
    }
}