package com.aiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Open Platform - Skill Server Application
 *
 * Main entry point for the skill server that handles:
 * - WebSocket connections for real-time communication
 * - Skill session management
 * - Message persistence
 * - Protocol adaptation
 */
@SpringBootApplication
@EnableScheduling
public class SkillServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillServerApplication.class, args);
    }
}