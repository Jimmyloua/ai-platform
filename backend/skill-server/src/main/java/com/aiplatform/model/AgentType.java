package com.aiplatform.model;

/**
 * Agent type enumeration for protocol adaptation.
 * Determines which protocol adapter to use for message transformation.
 */
public enum AgentType {
    /**
     * Default skill protocol (current implementation)
     */
    DEFAULT("default"),

    /**
     * OpenCode/agent protocol (streaming agent messages)
     */
    OPENCODE("opencode"),

    /**
     * OpenAI-compatible protocol (chat completions API)
     */
    OPENAI("openai");

    private final String value;

    AgentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse agent type from string value
     */
    public static AgentType fromValue(String value) {
        if (value == null || value.isEmpty()) {
            return DEFAULT;
        }
        for (AgentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return DEFAULT;
    }
}