package com.aiplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OpenCode protocol message types for agent communication.
 * Supports streaming agent-style interactions.
 */
public class OpenCodeMessage {

    /**
     * Base OpenCode message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BaseMessage implements WebSocketMessage.WebSocketMessageBase {
        private String type;
        private String sessionId;
        private Long timestamp;
    }

    /**
     * OpenCode agent execution request
     * Client -> Server: Execute an agent with messages and tools
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentExecuteRequest implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.execute";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            /**
             * Conversation messages
             */
            private List<Message> messages;

            /**
             * Available tools/functions
             */
            private List<Tool> tools;

            /**
             * Whether to stream responses
             */
            private boolean stream;

            /**
             * Model to use (optional)
             */
            private String model;

            /**
             * Additional options
             */
            private Map<String, Object> options;

            /**
             * Skill ID to execute (for skill-based agents)
             */
            private String skillId;

            /**
             * Skill name (alternative to skillId)
             */
            private String skillName;

            /**
             * Channel source
             */
            private String channel;
        }
    }

    /**
     * OpenCode message in conversation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
        private String name;
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;

        /**
         * Create a user message
         */
        public static Message user(String content) {
            return Message.builder()
                    .role("user")
                    .content(content)
                    .build();
        }

        /**
         * Create an assistant message
         */
        public static Message assistant(String content) {
            return Message.builder()
                    .role("assistant")
                    .content(content)
                    .build();
        }

        /**
         * Create a system message
         */
        public static Message system(String content) {
            return Message.builder()
                    .role("system")
                    .content(content)
                    .build();
        }
    }

    /**
     * Tool/function definition
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool {
        private String type = "function";
        private FunctionDefinition function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionDefinition {
            private String name;
            private String description;
            private Map<String, Object> parameters;
        }
    }

    /**
     * Tool call from agent
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type = "function";
        private FunctionCall function;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FunctionCall {
            private String name;
            private String arguments;
        }
    }

    /**
     * Agent response chunk (streaming)
     * Server -> Client: Streaming response chunks
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentResponseChunk implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.response.chunk";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            /**
             * Chunk ID for sequencing
             */
            private int chunkId;

            /**
             * Message delta (partial content)
             */
            private MessageDelta delta;

            /**
             * Whether this is the final chunk
             */
            private boolean done;

            /**
             * Finish reason (if done)
             */
            private String finishReason;
        }
    }

    /**
     * Message delta for streaming
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageDelta {
        private String role;
        private String content;
        @JsonProperty("tool_calls")
        private List<ToolCallDelta> toolCalls;
    }

    /**
     * Tool call delta for streaming
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCallDelta {
        private int index;
        private String id;
        private String type;
        private FunctionCallDelta function;
    }

    /**
     * Function call delta for streaming
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCallDelta {
        private String name;
        private String arguments;
    }

    /**
     * Agent result response (non-streaming or final)
     * Server -> Client: Final agent result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentResultResponse implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.result";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private Message message;
            private Usage usage;
            private Map<String, Object> metadata;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Usage {
                private int inputTokens;
                private int outputTokens;
                private long durationMs;
            }
        }
    }

    /**
     * Agent error response
     * Server -> Client: Error during agent execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentErrorResponse implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.error";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private String code;
            private String message;
            private String details;
        }
    }

    /**
     * Agent tool call request
     * Server -> Client: Agent requesting tool execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentToolCallRequest implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.tool.call";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private List<ToolCall> toolCalls;
        }
    }

    /**
     * Agent tool result submission
     * Client -> Server: Submit tool execution results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentToolResultRequest implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.tool.result";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private List<ToolResult> toolResults;
        }
    }

    /**
     * Tool execution result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResult {
        @JsonProperty("tool_call_id")
        private String toolCallId;
        private String role = "tool";
        private String content;
        private String name;
    }

    /**
     * Agent cancel request
     * Client -> Server: Cancel agent execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentCancelRequest implements WebSocketMessage.WebSocketMessageBase {
        public static final String TYPE = "agent.cancel";

        private String type = TYPE;
        private String sessionId;
    }
}