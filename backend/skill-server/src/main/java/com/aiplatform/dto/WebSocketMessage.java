package com.aiplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket message types for skill protocol.
 * All nested message classes implement WebSocketMessageBase for type compatibility.
 */
public class WebSocketMessage {

    /**
     * Base interface for all WebSocket message types.
     * Allows different message protocol types to be used interchangeably.
     */
    public interface WebSocketMessageBase {
        String getType();
        String getSessionId();
    }

    /**
     * Base message structure
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BaseMessage implements WebSocketMessageBase {
        private String type;
        private String sessionId;
        private Long timestamp;
        private Map<String, Object> metadata;
    }

    /**
     * Client -> Server: Execute a skill
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillExecuteRequest implements WebSocketMessageBase {
        public static final String TYPE = "skill.execute";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private String skillId;
            private String skillName;
            private Map<String, Object> input;
            private Map<String, Object> options;
            private String channel;
        }
    }

    /**
     * Server -> Client: Progress update
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillProgressResponse implements WebSocketMessageBase {
        public static final String TYPE = "skill.progress";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private int progress;
            private String step;
            private String message;
            private Map<String, Object> data;
        }
    }

    /**
     * Server -> Client: Skill result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillResultResponse implements WebSocketMessageBase {
        public static final String TYPE = "skill.result";

        private String type = TYPE;
        private String sessionId;
        private Payload payload;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Payload {
            private Object result;
            private Map<String, Object> metadata;
            private Usage usage;

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
     * Server -> Client: Skill error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillErrorResponse implements WebSocketMessageBase {
        public static final String TYPE = "skill.error";

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
     * Client -> Server: Pause skill execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillPauseRequest implements WebSocketMessageBase {
        public static final String TYPE = "skill.pause";

        private String type = TYPE;
        private String sessionId;
    }

    /**
     * Client -> Server: Resume skill execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillResumeRequest implements WebSocketMessageBase {
        public static final String TYPE = "skill.resume";

        private String type = TYPE;
        private String sessionId;
    }

    /**
     * Client -> Server: Cancel skill execution
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCancelRequest implements WebSocketMessageBase {
        public static final String TYPE = "skill.cancel";

        private String type = TYPE;
        private String sessionId;
    }

    /**
     * OpenAI-compatible chat message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
        private String name;
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
        @JsonProperty("tool_call_id")
        private String toolCallId;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ToolCall {
            private String id;
            private String type;
            private Function function;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Function {
                private String name;
                private String arguments;
            }
        }
    }

    /**
     * Streaming response chunk (OpenAI-compatible)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatCompletionChunk {
        public static final String TYPE = "chat.completion.chunk";

        private String id;
        private String object = "chat.completion.chunk";
        private long created;
        private String model;
        private List<Choice> choices;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Choice {
            private int index;
            private Delta delta;
            private String finishReason;

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Delta {
                private String role;
                private String content;
                @JsonProperty("tool_calls")
                private List<ChatMessage.ToolCall> toolCalls;
            }
        }
    }

    /**
     * Heartbeat message
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Heartbeat implements WebSocketMessageBase {
        public static final String TYPE = "heartbeat";

        private String type = TYPE;
        private long timestamp;

        @Override
        public String getSessionId() {
            return null; // Heartbeat has no session
        }
    }
}