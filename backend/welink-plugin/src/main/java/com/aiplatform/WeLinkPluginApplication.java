package com.aiplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WeLink Plugin Application
 *
 * Provides integration between WeLink IM platform and the AI Platform.
 * Handles WebSocket connections, message routing, and skill execution.
 */
@SpringBootApplication
public class WeLinkPluginApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeLinkPluginApplication.class, args);
    }
}